package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.jspecify.annotations.Nullable;

/** Shared runtime lookup and compatibility validation support for the SQLite native bridge. */
final class SqliteNativeRuntimePolicy {
  private static final String RETIRED_LIBRARY_ENVIRONMENT_VARIABLE = "FINGRIND_SQLITE_LIBRARY";
  private static final String SOURCE_CHECKOUT_ROOT_SYSTEM_PROPERTY =
      "fingrind.source-checkout.root";
  private static final String SOURCE_CHECKOUT_BUILD_ROOT_SYSTEM_PROPERTY =
      "fingrind.source-checkout.build-root";
  private static final String SOURCE_CHECKOUT_ROOT_MANIFEST_ATTRIBUTE =
      "FinGrind-Source-Checkout-Root";
  private static final String SOURCE_CHECKOUT_BUILD_ROOT_MANIFEST_ATTRIBUTE =
      "FinGrind-Source-Checkout-Build-Root";

  private SqliteNativeRuntimePolicy() {}

  static int compareVersions(String leftVersion, String rightVersion) {
    int[] leftParts = parseVersionParts(leftVersion);
    int[] rightParts = parseVersionParts(rightVersion);
    int parts = Math.max(leftParts.length, rightParts.length);
    for (int index = 0; index < parts; index++) {
      int left = index < leftParts.length ? leftParts[index] : 0;
      int right = index < rightParts.length ? rightParts[index] : 0;
      if (left != right) {
        return Integer.compare(left, right);
      }
    }
    return 0;
  }

  static String requireSupportedVersion(
      String loadedVersion,
      String libraryMode,
      String loadedSqlite3mcVersion,
      String loadedSourceId) {
    Objects.requireNonNull(loadedVersion, "loadedVersion");
    Objects.requireNonNull(libraryMode, "libraryMode");
    Objects.requireNonNull(loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
    Objects.requireNonNull(loadedSourceId, "loadedSourceId");
    if (compareVersions(loadedVersion, SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION) < 0) {
      throw new UnsupportedSqliteVersionException(
          loadedVersion,
          SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
          libraryMode,
          loadedSqlite3mcVersion,
          loadedSourceId);
    }
    return loadedVersion;
  }

  static String requireSupportedVersion(String loadedVersion, String libraryMode) {
    return requireSupportedVersion(
        loadedVersion,
        libraryMode,
        SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
  }

  static String requireSupportedSqlite3mcVersion(
      String loadedVersion, String libraryMode, String loadedSqliteVersion, String loadedSourceId) {
    Objects.requireNonNull(loadedVersion, "loadedVersion");
    Objects.requireNonNull(libraryMode, "libraryMode");
    Objects.requireNonNull(loadedSqliteVersion, "loadedSqliteVersion");
    Objects.requireNonNull(loadedSourceId, "loadedSourceId");
    if (!SqliteRuntime.REQUIRED_SQLITE3MC_VERSION.equals(loadedVersion)) {
      throw new UnsupportedSqliteMultipleCiphersVersionException(
          loadedVersion,
          SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
          libraryMode,
          loadedSqliteVersion,
          loadedSourceId);
    }
    return loadedVersion;
  }

  static String requireSupportedSqlite3mcVersion(String loadedVersion, String libraryMode) {
    return requireSupportedSqlite3mcVersion(
        loadedVersion,
        libraryMode,
        SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
  }

  static String requireSupportedSourceId(
      String loadedSourceId,
      String libraryMode,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion) {
    Objects.requireNonNull(loadedSourceId, "loadedSourceId");
    Objects.requireNonNull(libraryMode, "libraryMode");
    Objects.requireNonNull(loadedSqliteVersion, "loadedSqliteVersion");
    Objects.requireNonNull(loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
    if (!SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID.equals(loadedSourceId)) {
      throw new UnsupportedSqliteSourceIdException(
          loadedSourceId,
          SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
          libraryMode,
          loadedSqliteVersion,
          loadedSqlite3mcVersion);
    }
    return loadedSourceId;
  }

  static String requireSupportedSourceId(String loadedSourceId, String libraryMode) {
    return requireSupportedSourceId(
        loadedSourceId,
        libraryMode,
        SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
        SqliteRuntime.REQUIRED_SQLITE3MC_VERSION);
  }

  static void requireSupportedCompileOptions(
      MethodHandle compileOptionUsedHandle,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String loadedSqliteSourceId,
      String libraryMode) {
    Objects.requireNonNull(compileOptionUsedHandle, "compileOptionUsedHandle");
    Objects.requireNonNull(loadedSqliteVersion, "loadedSqliteVersion");
    Objects.requireNonNull(loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
    Objects.requireNonNull(loadedSqliteSourceId, "loadedSqliteSourceId");
    Objects.requireNonNull(libraryMode, "libraryMode");
    var missingCompileOptions =
        SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS.stream()
            .filter(option -> !compileOptionUsed(compileOptionUsedHandle, option))
            .toList();
    var forbiddenCompileOptions =
        SqliteRuntime.FORBIDDEN_SQLITE_COMPILE_OPTIONS.stream()
            .filter(option -> compileOptionUsed(compileOptionUsedHandle, option))
            .toList();
    if (!missingCompileOptions.isEmpty() || !forbiddenCompileOptions.isEmpty()) {
      throw new UnsupportedSqliteCompileOptionsException(
          loadedSqliteVersion,
          loadedSqlite3mcVersion,
          loadedSqliteSourceId,
          libraryMode,
          missingCompileOptions,
          forbiddenCompileOptions);
    }
  }

  static void requireSupportedCompileOptions(
      MethodHandle compileOptionUsedHandle,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String libraryMode) {
    requireSupportedCompileOptions(
        compileOptionUsedHandle,
        loadedSqliteVersion,
        loadedSqlite3mcVersion,
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        libraryMode);
  }

  static boolean compileOptionUsed(MethodHandle compileOptionUsedHandle, String compileOption) {
    Objects.requireNonNull(compileOptionUsedHandle, "compileOptionUsedHandle");
    Objects.requireNonNull(compileOption, "compileOption");
    try (Arena arena = Arena.ofConfined()) {
      return SqliteNativeInvocation.invoke(
          "Failed to read the SQLite compile option: " + compileOption,
          () ->
              SqliteNativeCalls.addressToInt(compileOptionUsedHandle)
                      .invoke(arena.allocateFrom(compileOption))
                  != 0);
    }
  }

  static SqliteLibraryTarget configuredLibraryTarget(@Nullable String configuredLibraryPath) {
    return configuredLibraryTarget(
        configuredLibraryPath,
        null,
        SqliteNativeRuntimePolicy::sourceCheckoutRoots,
        SqliteNativeRuntimePolicy::sourceCheckoutBuildRoot);
  }

  static SqliteLibraryTarget configuredLibraryTarget(
      @Nullable String configuredLibraryPath, @Nullable String bundleHomePath) {
    return configuredLibraryTarget(
        configuredLibraryPath,
        bundleHomePath,
        SqliteNativeRuntimePolicy::sourceCheckoutRoots,
        SqliteNativeRuntimePolicy::sourceCheckoutBuildRoot);
  }

  static SqliteLibraryTarget configuredLibraryTarget(
      @Nullable String configuredLibraryPath,
      @Nullable String bundleHomePath,
      Supplier<List<Path>> sourceCheckoutRootsSupplier,
      Supplier<@Nullable Path> sourceCheckoutBuildRootSupplier) {
    String normalizedConfiguredPath = normalizeNullableConfiguredLibraryPath(configuredLibraryPath);
    if (normalizedConfiguredPath != null) {
      throw retiredConfiguredRuntimeFailure(normalizedConfiguredPath);
    }
    String normalizedBundleHomePath = normalizeNullablePath(bundleHomePath);
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

  private static @Nullable String normalizeNullableConfiguredLibraryPath(
      @Nullable String configuredLibraryPath) {
    if (configuredLibraryPath == null) {
      return null;
    }
    String normalizedPath = configuredLibraryPath.strip();
    if (normalizedPath.isEmpty()) {
      return null;
    }
    return Path.of(normalizedPath).toAbsolutePath().normalize().toString();
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

  private static SqliteLibraryTarget bundledLibraryTarget(String normalizedBundleHomePath) {
    Path bundleLibraryPath =
        Path.of(normalizedBundleHomePath)
            .resolve("lib")
            .resolve("native")
            .resolve(supportedNativeLibraryFileName());
    if (!Files.isRegularFile(bundleLibraryPath)) {
      throw new ManagedSqliteRuntimeUnavailableException(
          "FinGrind bundle home at "
              + normalizedBundleHomePath
              + " does not contain the managed SQLite library at "
              + bundleLibraryPath
              + ". Use the published FinGrind bundle launcher as extracted (bin/fingrind on macOS/Linux or bin\\fingrind.ps1 on Windows; bin\\fingrind.cmd remains a compatibility wrapper), or from a local source checkout run ./gradlew prepareManagedSqlite and rerun the generated launcher or developer raw JAR from that checkout.");
    }
    return new SqliteLibraryTarget(
        SqliteRuntime.LIBRARY_MODE,
        SqliteRuntimeProvenance.BUNDLE_MANAGED,
        bundleLibraryPath.toString());
  }

  private static ManagedSqliteRuntimeUnavailableException missingLibraryTargetFailure() {
    return new ManagedSqliteRuntimeUnavailableException(
        "FinGrind could not locate the managed SQLite runtime. Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.ps1 on Windows; bin\\fingrind.cmd remains a compatibility wrapper), or from a local source checkout run ./gradlew prepareManagedSqlite and rerun the generated launcher or developer raw JAR from that checkout.");
  }

  private static ManagedSqliteRuntimeUnavailableException retiredConfiguredRuntimeFailure(
      String normalizedConfiguredPath) {
    return new ManagedSqliteRuntimeUnavailableException(
        "The operator-configured SQLite runtime override via "
            + RETIRED_LIBRARY_ENVIRONMENT_VARIABLE
            + " has been removed. FinGrind now loads only publisher-authenticated bundle runtimes or source-checkout-managed runtimes from a prepared checkout. Remove "
            + RETIRED_LIBRARY_ENVIRONMENT_VARIABLE
            + "="
            + normalizedConfiguredPath
            + " from the current environment and rerun from a supported launcher.");
  }

  private static @Nullable SqliteLibraryTarget sourceCheckoutLibraryTarget(
      Supplier<List<Path>> sourceCheckoutRootsSupplier,
      Supplier<@Nullable Path> sourceCheckoutBuildRootSupplier) {
    Objects.requireNonNull(sourceCheckoutRootsSupplier, "sourceCheckoutRootsSupplier");
    Objects.requireNonNull(sourceCheckoutBuildRootSupplier, "sourceCheckoutBuildRootSupplier");
    @Nullable Path sourceCheckoutBuildRoot = sourceCheckoutBuildRootSupplier.get();
    for (Path sourceCheckoutRoot : sourceCheckoutRootsSupplier.get()) {
      Path managedLibraryPath =
          sourceCheckoutManagedLibraryPath(sourceCheckoutRoot, sourceCheckoutBuildRoot);
      if (managedLibraryPath != null) {
        return new SqliteLibraryTarget(
            SqliteRuntime.LIBRARY_MODE,
            SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
            managedLibraryPath.toString());
      }
    }
    return null;
  }

  private static List<Path> sourceCheckoutRoots() {
    Path codeSourcePath = codeSourcePath();
    return sourceCheckoutRoots(
        normalizeNullablePath(System.getProperty(SOURCE_CHECKOUT_ROOT_SYSTEM_PROPERTY)),
        sourceCheckoutRootFromManifest(codeSourcePath, SqliteNativeRuntimePolicy::manifestFromJar),
        sourceCheckoutRootFromCodeSource(codeSourcePath));
  }

  private static @Nullable Path sourceCheckoutBuildRoot() {
    Path codeSourcePath = codeSourcePath();
    return sourceCheckoutBuildRoot(
        normalizeNullablePath(System.getProperty(SOURCE_CHECKOUT_BUILD_ROOT_SYSTEM_PROPERTY)),
        sourceCheckoutBuildRootFromManifest(
            codeSourcePath, SqliteNativeRuntimePolicy::manifestFromJar));
  }

  static List<Path> sourceCheckoutRoots(
      @Nullable String configuredRootPath,
      @Nullable String manifestRootPath,
      @Nullable String codeSourceRootPath) {
    Set<Path> candidates = new LinkedHashSet<>();
    addSourceCheckoutRoots(candidates, configuredRootPath);
    addSourceCheckoutRoots(candidates, manifestRootPath);
    addSourceCheckoutRoots(candidates, codeSourceRootPath);
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

  static @Nullable String sourceCheckoutRootFromManifest(
      @Nullable Path codeSourcePath, ManifestReader manifestReader) {
    if (codeSourcePath == null || !Files.isRegularFile(codeSourcePath)) {
      return null;
    }
    try {
      Manifest manifest = manifestReader.read(codeSourcePath);
      if (manifest == null) {
        return null;
      }
      return normalizeNullablePath(
          manifest.getMainAttributes().getValue(SOURCE_CHECKOUT_ROOT_MANIFEST_ATTRIBUTE));
    } catch (IOException exception) {
      return null;
    }
  }

  static @Nullable String sourceCheckoutBuildRootFromManifest(
      @Nullable Path codeSourcePath, ManifestReader manifestReader) {
    if (codeSourcePath == null || !Files.isRegularFile(codeSourcePath)) {
      return null;
    }
    try {
      Manifest manifest = manifestReader.read(codeSourcePath);
      if (manifest == null) {
        return null;
      }
      return normalizeNullablePath(
          manifest.getMainAttributes().getValue(SOURCE_CHECKOUT_BUILD_ROOT_MANIFEST_ATTRIBUTE));
    } catch (IOException exception) {
      return null;
    }
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

  private static @Nullable Path codeSourcePath() {
    return codeSourcePath(SqliteNativeRuntimePolicy.class.getProtectionDomain().getCodeSource());
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

  private static @Nullable Path sourceCheckoutManagedLibraryPath(
      Path sourceCheckoutRoot, @Nullable Path sourceCheckoutBuildRoot) {
    return sourceCheckoutManagedLibraryPath(
        sourceCheckoutRoot, sourceCheckoutBuildRoot, SqliteNativeRuntimePolicy::findManagedLibrary);
  }

  static @Nullable Path sourceCheckoutManagedLibraryPath(
      Path sourceCheckoutRoot, ManagedLibraryFinder managedLibraryFinder) {
    return sourceCheckoutManagedLibraryPath(sourceCheckoutRoot, null, managedLibraryFinder);
  }

  static @Nullable Path sourceCheckoutManagedLibraryPath(
      Path sourceCheckoutRoot,
      @Nullable Path sourceCheckoutBuildRoot,
      ManagedLibraryFinder managedLibraryFinder) {
    String expectedFileName = supportedNativeLibraryFileName();
    String expectedClassifier = supportedHostClassifier();
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
        return null;
      }
    }
    return null;
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

  private static @Nullable Path findManagedLibrary(Path managedSqliteRoot, String expectedFileName)
      throws IOException {
    Path expectedLibraryPath = managedSqliteRoot.resolve(expectedFileName);
    return Files.isRegularFile(expectedLibraryPath) ? expectedLibraryPath : null;
  }

  private static Manifest manifestFromJar(Path codeSourcePath) throws IOException {
    try (JarFile jarFile = new JarFile(codeSourcePath.toFile())) {
      return jarFile.getManifest();
    }
  }

  static @Nullable Path sourceCheckoutBuildRoot(
      @Nullable String configuredBuildRootPath, @Nullable String manifestBuildRootPath) {
    String normalizedBuildRootPath =
        configuredBuildRootPath != null ? configuredBuildRootPath : manifestBuildRootPath;
    if (normalizedBuildRootPath == null) {
      return null;
    }
    return Path.of(normalizedBuildRootPath).toAbsolutePath().normalize();
  }

  static String supportedNativeLibraryFileName() {
    return supportedNativeLibraryFileName(
        supportedOperatingSystemId(System.getProperty("os.name", "")),
        System.getProperty("os.name"));
  }

  static String supportedNativeLibraryFileName(String operatingSystemId, String detectedOsName) {
    if ("macos".equals(operatingSystemId)) {
      return "libsqlite3.dylib";
    }
    if ("linux".equals(operatingSystemId)) {
      return "libsqlite3.so.0";
    }
    if ("windows".equals(operatingSystemId)) {
      return "sqlite3.dll";
    }
    throw new ManagedSqliteRuntimeUnavailableException(
        "FinGrind bundles currently support managed SQLite on macOS, Linux, and Windows only. Detected: "
            + detectedOsName);
  }

  static String supportedHostClassifier() {
    return supportedOperatingSystemId(System.getProperty("os.name", ""))
        + "-"
        + supportedArchitectureId(System.getProperty("os.arch", "unknown"));
  }

  static String supportedOperatingSystemId(String operatingSystemName) {
    String operatingSystem = operatingSystemName.toLowerCase(Locale.ROOT);
    if (operatingSystem.contains("mac")) {
      return "macos";
    }
    if (operatingSystem.contains("linux")) {
      return "linux";
    }
    if (operatingSystem.contains("windows")) {
      return "windows";
    }
    throw new ManagedSqliteRuntimeUnavailableException(
        "FinGrind bundles currently support managed SQLite on macOS, Linux, and Windows only. Detected: "
            + operatingSystemName);
  }

  static String supportedArchitectureId(String architectureName) {
    String architecture = architectureName.toLowerCase(Locale.ROOT);
    return switch (architecture) {
      case "arm64", "aarch64" -> "aarch64";
      case "amd64", "x86_64", "x64" -> "x86_64";
      default -> architecture.replaceAll("[^a-z0-9]+", "-");
    };
  }

  private static int[] parseVersionParts(String version) {
    Objects.requireNonNull(version, "version");
    String[] parts = version.split("\\.", -1);
    int[] parsedParts = new int[parts.length];
    for (int index = 0; index < parts.length; index++) {
      try {
        parsedParts[index] = Integer.parseInt(parts[index]);
      } catch (NumberFormatException exception) {
        throw new IllegalStateException("Unsupported SQLite version string: " + version, exception);
      }
    }
    return parsedParts;
  }

  /** Opens one manifest view for one code-source artifact. */
  @FunctionalInterface
  interface ManifestReader {
    /** Reads the manifest for the provided code-source artifact. */
    @Nullable Manifest read(Path codeSourcePath) throws IOException;
  }

  /** Locates one managed SQLite library candidate under one managed-runtime root. */
  @FunctionalInterface
  interface ManagedLibraryFinder {
    /** Finds the managed SQLite library path under the provided managed-runtime root. */
    @Nullable Path find(Path managedSqliteRoot, String expectedFileName) throws IOException;
  }
}
