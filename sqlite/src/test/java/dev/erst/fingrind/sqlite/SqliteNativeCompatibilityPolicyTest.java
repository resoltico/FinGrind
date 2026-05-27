package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
class SqliteNativeCompatibilityPolicyTest extends SqliteNativeBridgeTestSupport {
  @Test
  void requireSupportedVersion_rejectsOlderRuntimeAndCompareVersionsOrdersDottedNumbers() {
    assertTrue(SqliteNativeCompatibilityPolicy.compareVersions("3.53.1", "3.52.9") > 0);
    assertEquals(-1, SqliteNativeCompatibilityPolicy.compareVersions("3.53", "3.53.1"));
    assertEquals(1, SqliteNativeCompatibilityPolicy.compareVersions("3.53.1", "3.53"));
    assertThrows(
        IllegalStateException.class,
        () -> SqliteNativeCompatibilityPolicy.compareVersions("3.bad.0", "3.53.1"));
    UnsupportedSqliteVersionException exception =
        assertThrows(
            UnsupportedSqliteVersionException.class,
            () ->
                SqliteNativeCompatibilityPolicy.requireSupportedVersion("3.51.0", "managed-only"));
    assertEquals("3.51.0", exception.loadedVersion());
    assertEquals("3.53.1", exception.requiredMinimumVersion());
    assertEquals("managed-only", exception.libraryMode());
  }

  @Test
  void requireSupportedSqlite3mcVersion_rejectsUnexpectedRuntime() {
    UnsupportedSqliteMultipleCiphersVersionException exception =
        assertThrows(
            UnsupportedSqliteMultipleCiphersVersionException.class,
            () ->
                SqliteNativeCompatibilityPolicy.requireSupportedSqlite3mcVersion(
                    "2.3.2", "managed-only"));
    assertEquals("2.3.2", exception.loadedVersion());
    assertEquals("2.3.4", exception.requiredVersion());
    assertEquals("managed-only", exception.libraryMode());
  }

  @Test
  void requireSupportedCompileOptions_rejectsMissingHardeningOptions() {
    UnsupportedSqliteCompileOptionsException exception =
        assertThrows(
            UnsupportedSqliteCompileOptionsException.class,
            () ->
                SqliteNativeCompatibilityPolicy.requireSupportedCompileOptions(
                    constantMethodHandle(0, MemorySegment.class),
                    "3.53.1",
                    "2.3.4",
                    "managed-only"));
    assertEquals("3.53.1", exception.loadedSqliteVersion());
    assertEquals("2.3.4", exception.loadedSqlite3mcVersion());
    assertEquals("managed-only", exception.libraryMode());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS, exception.missingCompileOptions());
    assertEquals(java.util.List.of(), exception.forbiddenCompileOptions());
  }

  @Test
  void requireSupportedCompileOptions_rejectsForbiddenCompileOptions() {
    UnsupportedSqliteCompileOptionsException exception =
        assertThrows(
            UnsupportedSqliteCompileOptionsException.class,
            () ->
                SqliteNativeCompatibilityPolicy.requireSupportedCompileOptions(
                    constantMethodHandle(1, MemorySegment.class),
                    "3.53.1",
                    "2.3.4",
                    "managed-only"));
    assertEquals(java.util.List.of(), exception.missingCompileOptions());
    assertEquals(
        SqliteRuntime.FORBIDDEN_SQLITE_COMPILE_OPTIONS, exception.forbiddenCompileOptions());
  }

  @Test
  void requireSupportedSourceId_rejectsUnexpectedRuntimeAndAcceptsCanonicalPin() {
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        SqliteNativeCompatibilityPolicy.requireSupportedSourceId(
            SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, "managed-only"));
    UnsupportedSqliteSourceIdException exception =
        assertThrows(
            UnsupportedSqliteSourceIdException.class,
            () ->
                SqliteNativeCompatibilityPolicy.requireSupportedSourceId(
                    "2026-04-09 unexpected-source-id", "managed-only", "3.53.1", "2.3.4"));
    assertEquals("2026-04-09 unexpected-source-id", exception.loadedSourceId());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, exception.requiredSourceId());
    assertEquals("managed-only", exception.libraryMode());
    assertEquals("3.53.1", exception.loadedSqliteVersion());
    assertEquals("2.3.4", exception.loadedSqlite3mcVersion());
  }

  @Test
  void sqliteNativeApi_rejectsBlankLoadedVersions() {
    Object[] blankLoadedVersionArguments = defaultSqliteApiArguments();
    blankLoadedVersionArguments[SQLITE_API_ARGUMENT_LOADED_VERSION] = " ";
    assertThrows(IllegalArgumentException.class, () -> buildSqliteApi(blankLoadedVersionArguments));
    Object[] blankSqlite3mcArguments = defaultSqliteApiArguments();
    blankSqlite3mcArguments[SQLITE_API_ARGUMENT_LOADED_SQLITE3MC_VERSION] = " ";
    assertThrows(IllegalArgumentException.class, () -> buildSqliteApi(blankSqlite3mcArguments));
    Object[] blankSourceIdArguments = defaultSqliteApiArguments();
    blankSourceIdArguments[SQLITE_API_ARGUMENT_LOADED_SOURCE_ID] = " ";
    assertThrows(IllegalArgumentException.class, () -> buildSqliteApi(blankSourceIdArguments));
    Object[] blankLibraryPathArguments = defaultSqliteApiArguments();
    blankLibraryPathArguments[SQLITE_API_ARGUMENT_LOADED_LIBRARY_PATH] = " ";
    assertThrows(IllegalArgumentException.class, () -> buildSqliteApi(blankLibraryPathArguments));
  }
}
