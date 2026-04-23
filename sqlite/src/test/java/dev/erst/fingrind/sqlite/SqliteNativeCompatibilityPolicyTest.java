package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
@NullUnmarked
class SqliteNativeCompatibilityPolicyTest extends SqliteNativeBridgeTestSupport {

  @Test
  void requireSupportedVersion_rejectsOlderRuntimeAndCompareVersionsOrdersDottedNumbers() {
    assertTrue(SqliteNativeRuntimePolicy.compareVersions("3.53.0", "3.52.9") > 0);
    assertEquals(0, SqliteNativeRuntimePolicy.compareVersions("3.53", "3.53.0"));
    assertEquals(0, SqliteNativeRuntimePolicy.compareVersions("3.53.0", "3.53"));
    assertThrows(
        IllegalStateException.class,
        () -> SqliteNativeRuntimePolicy.compareVersions("3.bad.0", "3.53.0"));

    UnsupportedSqliteVersionException exception =
        assertThrows(
            UnsupportedSqliteVersionException.class,
            () -> SqliteNativeRuntimePolicy.requireSupportedVersion("3.51.0", "managed-only"));

    assertEquals("3.51.0", exception.loadedVersion());
    assertEquals("3.53.0", exception.requiredMinimumVersion());
    assertEquals("managed-only", exception.libraryMode());
  }

  @Test
  void requireSupportedSqlite3mcVersion_rejectsUnexpectedRuntime() {
    UnsupportedSqliteMultipleCiphersVersionException exception =
        assertThrows(
            UnsupportedSqliteMultipleCiphersVersionException.class,
            () ->
                SqliteNativeRuntimePolicy.requireSupportedSqlite3mcVersion(
                    "2.3.2", "managed-only"));

    assertEquals("2.3.2", exception.loadedVersion());
    assertEquals("2.3.3", exception.requiredVersion());
    assertEquals("managed-only", exception.libraryMode());
  }

  @Test
  void requireSupportedCompileOptions_rejectsMissingHardeningOptions() {
    UnsupportedSqliteCompileOptionsException exception =
        assertThrows(
            UnsupportedSqliteCompileOptionsException.class,
            () ->
                SqliteNativeRuntimePolicy.requireSupportedCompileOptions(
                    constantMethodHandle(0, MemorySegment.class),
                    "3.53.0",
                    "2.3.3",
                    "managed-only"));

    assertEquals("3.53.0", exception.loadedSqliteVersion());
    assertEquals("2.3.3", exception.loadedSqlite3mcVersion());
    assertEquals("managed-only", exception.libraryMode());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS, exception.missingCompileOptions());
  }

  @Test
  void sqliteNativeApi_rejectsBlankLoadedVersions() {
    Object[] blankLoadedVersionArguments = defaultSqliteApiArguments();
    blankLoadedVersionArguments[22] = " ";

    assertThrows(IllegalArgumentException.class, () -> buildSqliteApi(blankLoadedVersionArguments));

    Object[] blankSqlite3mcArguments = defaultSqliteApiArguments();
    blankSqlite3mcArguments[23] = " ";

    assertThrows(IllegalArgumentException.class, () -> buildSqliteApi(blankSqlite3mcArguments));
  }
}
