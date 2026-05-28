package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** Compatibility validation for one loaded SQLite native runtime. */
final class SqliteNativeCompatibilityPolicy {
  private SqliteNativeCompatibilityPolicy() {}

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
              SqliteNativeCallAdapter.adapt(
                          SqliteNativeCalls.AddressToIntCall.class, compileOptionUsedHandle)
                      .invoke(arena.allocateFrom(compileOption))
                  != 0);
    }
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
