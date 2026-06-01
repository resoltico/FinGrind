package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Source-checkout cues for locating one managed SQLite runtime under a prepared checkout. */
final class SqliteSourceCheckoutRuntimeLocator {
  private static final String SOURCE_CHECKOUT_ROOT_SYSTEM_PROPERTY =
      "fingrind.source-checkout.root";
  private static final String SOURCE_CHECKOUT_BUILD_ROOT_SYSTEM_PROPERTY =
      "fingrind.source-checkout.build-root";

  private SqliteSourceCheckoutRuntimeLocator() {}

  static List<Path> sourceCheckoutRoots() {
    Path codeSourcePath = codeSourcePath();
    return sourceCheckoutRoots(
        normalizeNullablePath(System.getProperty(SOURCE_CHECKOUT_ROOT_SYSTEM_PROPERTY)),
        sourceCheckoutRootFromCodeSource(codeSourcePath));
  }

  static @Nullable Path sourceCheckoutBuildRoot() {
    return sourceCheckoutBuildRoot(
        normalizeNullablePath(System.getProperty(SOURCE_CHECKOUT_BUILD_ROOT_SYSTEM_PROPERTY)));
  }

  static @Nullable Path sourceCheckoutBuildRoot(@Nullable String configuredBuildRootPath) {
    String normalizedBuildRootPath = normalizeNullablePath(configuredBuildRootPath);
    if (normalizedBuildRootPath == null) {
      return null;
    }
    return Path.of(normalizedBuildRootPath);
  }

  static List<Path> sourceCheckoutRoots(
      @Nullable String configuredRootPath, @Nullable String codeSourceRootPath) {
    Set<Path> candidates = new LinkedHashSet<>();
    addSourceCheckoutRoots(candidates, configuredRootPath);
    addSourceCheckoutRoots(candidates, codeSourceRootPath);
    return List.copyOf(candidates);
  }

  static @Nullable String sourceCheckoutRootFromCodeSource(@Nullable Path codeSourcePath) {
    if (codeSourcePath == null) {
      return null;
    }
    Path normalizedCodeSourcePath = codeSourcePath.toAbsolutePath().normalize();
    return Files.isRegularFile(normalizedCodeSourcePath)
        ? Objects.requireNonNull(
                normalizedCodeSourcePath.getParent(), "normalizedCodeSourcePath.getParent()")
            .toString()
        : normalizedCodeSourcePath.toString();
  }

  static @Nullable Path codeSourcePath(@Nullable CodeSource codeSource) {
    if (codeSource == null) {
      return null;
    }
    return codeSourcePath(
        codeSource.getLocation() == null ? null : codeSource.getLocation().toExternalForm());
  }

  static @Nullable Path codeSourcePath(@Nullable String codeSourceLocation) {
    if (codeSourceLocation == null) {
      return null;
    }
    try {
      return Path.of(URI.create(codeSourceLocation)).toAbsolutePath().normalize();
    } catch (Exception exception) {
      return null;
    }
  }

  static @Nullable Path sourceCheckoutManagedLibraryPath(
      Path sourceCheckoutRoot,
      SqliteManagedLibraryTargetLocator.ManagedLibraryFinder managedLibraryFinder) {
    return sourceCheckoutManagedLibraryPath(sourceCheckoutRoot, null, managedLibraryFinder);
  }

  static @Nullable Path sourceCheckoutManagedLibraryPath(
      Path sourceCheckoutRoot,
      @Nullable Path sourceCheckoutBuildRoot,
      SqliteManagedLibraryTargetLocator.ManagedLibraryFinder managedLibraryFinder) {
    String expectedFileName = SqliteHostPlatformDescriptor.supportedNativeLibraryFileName();
    String expectedClassifier = SqliteHostPlatformDescriptor.supportedHostClassifier();
    for (Path managedSqliteRoot :
        sourceCheckoutManagedLibraryRoots(sourceCheckoutRoot, sourceCheckoutBuildRoot)) {
      Path classifierRoot = managedSqliteRoot.resolve(expectedClassifier);
      if (!Files.isDirectory(classifierRoot)) {
        continue;
      }
      try {
        Path managedLibraryPath = managedLibraryFinder.find(classifierRoot, expectedFileName);
        if (managedLibraryPath != null) {
          return managedLibraryPath;
        }
      } catch (IOException exception) {
        throw new ManagedSqliteRuntimeUnavailableException(
            "FinGrind found the prepared source-checkout managed SQLite runtime root at "
                + classifierRoot
                + " but could not inspect it.",
            exception);
      }
    }
    return null;
  }

  private static @Nullable Path codeSourcePath() {
    return codeSourcePath(
        SqliteSourceCheckoutRuntimeLocator.class.getProtectionDomain().getCodeSource());
  }

  private static List<Path> sourceCheckoutManagedLibraryRoots(
      Path sourceCheckoutRoot, @Nullable Path sourceCheckoutBuildRoot) {
    Set<Path> candidates = new LinkedHashSet<>();
    if (sourceCheckoutBuildRoot != null) {
      candidates.add(
          sourceCheckoutBuildRoot.resolve("managed-sqlite").toAbsolutePath().normalize());
    }
    candidates.add(
        sourceCheckoutRoot.resolve("build").resolve("managed-sqlite").toAbsolutePath().normalize());
    return List.copyOf(candidates);
  }

  private static void addSourceCheckoutRoots(Set<Path> candidates, @Nullable String rawRootPath) {
    if (rawRootPath == null) {
      return;
    }
    Path candidate = Path.of(rawRootPath).toAbsolutePath().normalize();
    if (Files.isRegularFile(candidate)) {
      candidate = candidate.getParent();
    }
    while (candidate != null) {
      if (looksLikeSourceCheckoutRoot(candidate)) {
        candidates.add(candidate);
      }
      candidate = candidate.getParent();
    }
  }

  private static boolean looksLikeSourceCheckoutRoot(Path candidate) {
    return Files.isRegularFile(candidate.resolve("gradlew"))
        && Files.isDirectory(candidate.resolve("cli"));
  }

  private static @Nullable String normalizeNullablePath(@Nullable String path) {
    if (path == null) {
      return null;
    }
    String normalizedPath = path.strip();
    if (normalizedPath.isEmpty()) {
      return null;
    }
    return Path.of(normalizedPath).toAbsolutePath().normalize().toString();
  }
}
