package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared runtime lookup and compatibility validation support for the SQLite native bridge. */
final class SqliteNativeRuntimeSupport {
  private SqliteNativeRuntimeSupport() {}

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

  static String requireSupportedVersion(String loadedVersion, String libraryMode) {
    Objects.requireNonNull(loadedVersion, "loadedVersion");
    Objects.requireNonNull(libraryMode, "libraryMode");
    if (compareVersions(loadedVersion, SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION) < 0) {
      throw new UnsupportedSqliteVersionException(
          loadedVersion, SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION, libraryMode);
    }
    return loadedVersion;
  }

  static String requireSupportedSqlite3mcVersion(String loadedVersion, String libraryMode) {
    Objects.requireNonNull(loadedVersion, "loadedVersion");
    Objects.requireNonNull(libraryMode, "libraryMode");
    if (!SqliteRuntime.REQUIRED_SQLITE3MC_VERSION.equals(loadedVersion)) {
      throw new UnsupportedSqliteMultipleCiphersVersionException(
          loadedVersion, SqliteRuntime.REQUIRED_SQLITE3MC_VERSION, libraryMode);
    }
    return loadedVersion;
  }

  static void requireSupportedCompileOptions(
      MethodHandle compileOptionUsedHandle,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String libraryMode) {
    Objects.requireNonNull(compileOptionUsedHandle, "compileOptionUsedHandle");
    Objects.requireNonNull(loadedSqliteVersion, "loadedSqliteVersion");
    Objects.requireNonNull(loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
    Objects.requireNonNull(libraryMode, "libraryMode");
    var missingCompileOptions =
        SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS.stream()
            .filter(option -> !compileOptionUsed(compileOptionUsedHandle, option))
            .toList();
    if (!missingCompileOptions.isEmpty()) {
      throw new UnsupportedSqliteCompileOptionsException(
          loadedSqliteVersion, loadedSqlite3mcVersion, libraryMode, missingCompileOptions);
    }
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
    return new SqliteLibraryTarget(
        SqliteRuntime.LIBRARY_MODE, normalizeConfiguredLibraryPath(configuredLibraryPath));
  }

  static SqliteLibraryTarget configuredLibraryTarget(
      @Nullable String configuredLibraryPath, @Nullable String bundleHomePath) {
    String normalizedConfiguredPath = normalizeNullableConfiguredLibraryPath(configuredLibraryPath);
    if (normalizedConfiguredPath != null) {
      return new SqliteLibraryTarget(SqliteRuntime.LIBRARY_MODE, normalizedConfiguredPath);
    }
    String normalizedBundleHomePath = normalizeNullablePath(bundleHomePath);
    if (normalizedBundleHomePath != null) {
      return bundledLibraryTarget(normalizedBundleHomePath);
    }
    throw missingLibraryTargetFailure();
  }

  private static String normalizeConfiguredLibraryPath(@Nullable String configuredLibraryPath) {
    if (configuredLibraryPath == null) {
      throw missingLibraryTargetFailure();
    }
    String normalizedPath = configuredLibraryPath.trim();
    if (normalizedPath.isEmpty()) {
      throw new ManagedSqliteRuntimeUnavailableException(
          SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE + " must not be blank.");
    }
    return Path.of(normalizedPath).toAbsolutePath().normalize().toString();
  }

  private static @Nullable String normalizeNullableConfiguredLibraryPath(
      @Nullable String configuredLibraryPath) {
    if (configuredLibraryPath == null) {
      return null;
    }
    String normalizedPath = configuredLibraryPath.trim();
    if (normalizedPath.isEmpty()) {
      return null;
    }
    return Path.of(normalizedPath).toAbsolutePath().normalize().toString();
  }

  private static @Nullable String normalizeNullablePath(@Nullable String path) {
    if (path == null) {
      return null;
    }
    String normalizedPath = path.trim();
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
              + ". Use the published FinGrind bundle launcher as extracted (bin/fingrind on macOS/Linux or bin\\fingrind.ps1 on Windows; bin\\fingrind.cmd remains a compatibility wrapper), or for a local source checkout set "
              + SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE
              + " to the managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 shared library produced by ./gradlew prepareManagedSqlite.");
    }
    return new SqliteLibraryTarget(SqliteRuntime.LIBRARY_MODE, bundleLibraryPath.toString());
  }

  private static ManagedSqliteRuntimeUnavailableException missingLibraryTargetFailure() {
    return new ManagedSqliteRuntimeUnavailableException(
        "FinGrind could not locate the managed SQLite runtime. Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.ps1 on Windows; bin\\fingrind.cmd remains a compatibility wrapper), or for a local source checkout set "
            + SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE
            + " to the managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 shared library produced by ./gradlew prepareManagedSqlite.");
  }

  static String supportedNativeLibraryFileName() {
    String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (operatingSystem.contains("mac")) {
      return "libsqlite3.dylib";
    }
    if (operatingSystem.contains("linux")) {
      return "libsqlite3.so.0";
    }
    if (operatingSystem.contains("windows")) {
      return "sqlite3.dll";
    }
    throw new ManagedSqliteRuntimeUnavailableException(
        "FinGrind bundles currently support managed SQLite on macOS, Linux, and Windows only. Detected: "
            + System.getProperty("os.name"));
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
}
