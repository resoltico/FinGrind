package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Locates one supported managed SQLite library target for the active host and launch mode. */
final class SqliteManagedLibraryTargetLocator {
  private SqliteManagedLibraryTargetLocator() {}

  static SqliteLibraryTarget configuredLibraryTarget() {
    return configuredLibraryTarget(
        null,
        SqliteSourceCheckoutRuntimeLocator::sourceCheckoutRoots,
        SqliteSourceCheckoutRuntimeLocator::sourceCheckoutBuildRoot);
  }

  static SqliteLibraryTarget configuredLibraryTarget(@Nullable String bundleHomePath) {
    return configuredLibraryTarget(
        bundleHomePath,
        SqliteSourceCheckoutRuntimeLocator::sourceCheckoutRoots,
        SqliteSourceCheckoutRuntimeLocator::sourceCheckoutBuildRoot);
  }

  static SqliteLibraryTarget configuredLibraryTarget(
      @Nullable String bundleHomePath,
      Supplier<List<Path>> sourceCheckoutRootsSupplier,
      Supplier<@Nullable Path> sourceCheckoutBuildRootSupplier) {
    String normalizedBundleHomePath = normalizeNullableBundleHomePath(bundleHomePath);
    if (normalizedBundleHomePath != null) {
      return bundledLibraryTarget(normalizedBundleHomePath);
    }
    SqliteLibraryTarget sourceCheckoutLibraryTarget =
        sourceCheckoutLibraryTarget(sourceCheckoutRootsSupplier, sourceCheckoutBuildRootSupplier);
    if (sourceCheckoutLibraryTarget != null) {
      return sourceCheckoutLibraryTarget;
    }
    throw missingLibraryTargetFailure();
  }

  private static @Nullable SqliteLibraryTarget sourceCheckoutLibraryTarget(
      Supplier<List<Path>> sourceCheckoutRootsSupplier,
      Supplier<@Nullable Path> sourceCheckoutBuildRootSupplier) {
    Objects.requireNonNull(sourceCheckoutRootsSupplier, "sourceCheckoutRootsSupplier");
    Objects.requireNonNull(sourceCheckoutBuildRootSupplier, "sourceCheckoutBuildRootSupplier");
    @Nullable Path sourceCheckoutBuildRoot = sourceCheckoutBuildRootSupplier.get();
    for (Path sourceCheckoutRoot : sourceCheckoutRootsSupplier.get()) {
      Path managedLibraryPath =
          SqliteSourceCheckoutRuntimeLocator.sourceCheckoutManagedLibraryPath(
              sourceCheckoutRoot,
              sourceCheckoutBuildRoot,
              SqliteManagedLibraryTargetLocator::findManagedLibrary);
      if (managedLibraryPath != null) {
        return new SqliteLibraryTarget(
            SqliteRuntime.LIBRARY_MODE,
            SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
            managedLibraryPath.toString());
      }
    }
    return null;
  }

  private static @Nullable Path findManagedLibrary(Path managedSqliteRoot, String expectedFileName)
      throws IOException {
    Path expectedLibraryPath = managedSqliteRoot.resolve(expectedFileName);
    return Files.isRegularFile(expectedLibraryPath) ? expectedLibraryPath : null;
  }

  private static @Nullable String normalizeNullableBundleHomePath(@Nullable String bundleHomePath) {
    if (bundleHomePath == null) {
      return null;
    }
    String normalizedPath = bundleHomePath.strip();
    if (normalizedPath.isEmpty()) {
      return null;
    }
    return Path.of(normalizedPath).toAbsolutePath().normalize().toString();
  }

  private static SqliteLibraryTarget bundledLibraryTarget(String normalizedBundleHomePath) {
    Path bundleLibraryPath =
        Path.of(normalizedBundleHomePath)
            .resolve("lib")
            .resolve("native")
            .resolve(SqliteHostPlatformDescriptor.supportedNativeLibraryFileName());
    if (!Files.isRegularFile(bundleLibraryPath)) {
      throw new ManagedSqliteRuntimeUnavailableException(
          "FinGrind bundle home at "
              + normalizedBundleHomePath
              + " does not contain the managed SQLite library at "
              + bundleLibraryPath
              + ". Use a supported FinGrind launcher surface: the extracted published Linux bundle launcher (bin/fingrind), the published container image, or from a local source checkout run ./gradlew prepareManagedSqlite and rerun the generated launcher or developer raw JAR from that checkout.");
    }
    return new SqliteLibraryTarget(
        SqliteRuntime.LIBRARY_MODE,
        SqliteRuntimeProvenance.BUNDLE_MANAGED,
        bundleLibraryPath.toString());
  }

  private static ManagedSqliteRuntimeUnavailableException missingLibraryTargetFailure() {
    return new ManagedSqliteRuntimeUnavailableException(
        "FinGrind could not locate the managed SQLite runtime. Run a supported FinGrind launcher surface: the extracted published Linux bundle launcher (bin/fingrind), the published container image, or from a local source checkout run ./gradlew prepareManagedSqlite and rerun the generated launcher or developer raw JAR from that checkout.");
  }

  /** Locates one managed SQLite library candidate under one managed-runtime root. */
  @FunctionalInterface
  interface ManagedLibraryFinder {
    /** Finds the managed SQLite library path under the provided managed-runtime root. */
    @Nullable Path find(Path managedSqliteRoot, String expectedFileName) throws IOException;
  }
}
