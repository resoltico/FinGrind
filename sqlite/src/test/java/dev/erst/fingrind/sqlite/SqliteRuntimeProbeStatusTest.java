package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
@NullUnmarked
class SqliteRuntimeProbeStatusTest extends SqliteNativeBridgeTestSupport {

  @Test
  void sqliteRuntimeProbe_reportsManagedSupportedVersion() {
    SqliteRuntime.Probe runtimeProbe = SqliteRuntime.probe();

    assertEquals("sqlite-ffm-sqlite3mc", SqliteRuntime.STORAGE_DRIVER);
    assertEquals("sqlite", SqliteRuntime.STORAGE_ENGINE);
    assertEquals("required", SqliteRuntime.BOOK_PROTECTION_MODE);
    assertEquals("chacha20", SqliteRuntime.DEFAULT_BOOK_CIPHER);
    assertEquals("FINGRIND_SQLITE_LIBRARY", SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE);
    assertEquals("fingrind.bundle.home", SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY);
    assertEquals(
        java.util.List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
        SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS);
    assertEquals("3.53.0", SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION);
    assertEquals("2.3.3", SqliteRuntime.REQUIRED_SQLITE3MC_VERSION);
    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.Status.READY, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertFalse(SqliteRuntime.sqliteVersion().isBlank());
    assertEquals("2.3.3", SqliteRuntime.sqlite3MultipleCiphersVersion());
  }

  @Test
  void failureDetail_prefersMessageAndFallsBackToType() {
    assertEquals("boom", SqliteRuntime.failureDetail(new IllegalStateException("boom")));
    assertEquals("RuntimeException", SqliteRuntime.failureDetail(new RuntimeException()));
  }

  @Test
  void probe_reportsIncompatibleRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probe(
            () -> "managed-only",
            () -> {
              throw new UnsupportedSqliteVersionException("3.51.0", "3.53.0", "managed-only");
            },
            () -> "2.3.3",
            SqliteRuntime::failureDetail);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals("3.51.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertTrue(runtimeProbe.issue().contains("requires SQLite 3.53.0 or newer"));
  }

  @Test
  void probe_reportsUnavailableRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probe(
            () -> "managed-only",
            () -> {
              throw new IllegalStateException("sqlite runtime unavailable");
            },
            () -> "2.3.3",
            SqliteRuntime::failureDetail);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(SqliteRuntime.Status.UNAVAILABLE, runtimeProbe.status());
    assertEquals("sqlite runtime unavailable", runtimeProbe.issue());
    assertNull(runtimeProbe.loadedSqliteVersion());
    assertNull(runtimeProbe.loadedSqlite3mcVersion());
  }

  @Test
  void probe_reportsIncompatibleSqlite3mcRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probe(
            () -> "managed-only",
            () -> "3.53.0",
            () -> {
              throw new UnsupportedSqliteMultipleCiphersVersionException(
                  "2.3.2", "2.3.3", "managed-only");
            },
            SqliteRuntime::failureDetail);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.2", runtimeProbe.loadedSqlite3mcVersion());
    assertTrue(runtimeProbe.issue().contains("requires SQLite3 Multiple Ciphers 2.3.3"));
  }

  @Test
  void probe_reportsIncompatibleCompileOptionsRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probe(
            () -> "managed-only",
            () -> {
              throw new UnsupportedSqliteCompileOptionsException(
                  "3.53.0", "2.3.3", "managed-only", List.of("SECURE_DELETE"));
            },
            () -> {
              throw new AssertionError("sqlite3mc version lookup should not run");
            },
            SqliteRuntime::failureDetail);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertTrue(runtimeProbe.issue().contains("missing required compile options"));
  }

  @Test
  void runtimeProbeAndStatusExposeStableValueSemantics() {
    SqliteRuntime.Probe runtimeProbe =
        new SqliteRuntime.Probe(
            "managed-only", "3.53.0", "2.3.3", SqliteRuntime.Status.READY, "3.53.0", "2.3.3", null);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.Status.READY, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertNull(runtimeProbe.issue());
    assertEquals(
        runtimeProbe,
        new SqliteRuntime.Probe(
            "managed-only",
            "3.53.0",
            "2.3.3",
            SqliteRuntime.Status.READY,
            "3.53.0",
            "2.3.3",
            null));
    assertEquals(
        runtimeProbe.hashCode(),
        new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.READY,
                "3.53.0",
                "2.3.3",
                null)
            .hashCode());
    assertTrue(runtimeProbe.toString().contains("managed-only"));
    assertEquals("ready", SqliteRuntime.Status.READY.wireValue());
    assertEquals("unavailable", SqliteRuntime.Status.UNAVAILABLE.wireValue());
    assertEquals("incompatible", SqliteRuntime.Status.INCOMPATIBLE.wireValue());
  }

  @Test
  void runtimeProbe_rejectsInvalidStatusSpecificShapes() {
    assertThrows(
        NullPointerException.class,
        () -> new SqliteRuntime.Probe("managed-only", "3.53.0", "2.3.3", null, null, null, "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.READY,
                null,
                "2.3.3",
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.READY,
                "3.53.0",
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.READY,
                "3.53.0",
                "2.3.3",
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.UNAVAILABLE,
                "3.53.0",
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                "2.3.3",
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.INCOMPATIBLE,
                null,
                "2.3.3",
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.INCOMPATIBLE,
                "3.53.0",
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.INCOMPATIBLE,
                "3.51.0",
                "2.3.3",
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                " ", "3.53.0", "2.3.3", SqliteRuntime.Status.UNAVAILABLE, null, null, "boom"));
  }

  @Test
  void resultName_mapsKnownAndUnknownCodes() {
    assertEquals("SQLITE_OK", SqliteNativeErrors.resultName(0));
    assertEquals("SQLITE_ROW", SqliteNativeErrors.resultName(100));
    assertEquals("SQLITE_DONE", SqliteNativeErrors.resultName(101));
    assertEquals("SQLITE_CONSTRAINT_UNIQUE", SqliteNativeErrors.resultName(2067));
    assertEquals("SQLITE_CONSTRAINT_PRIMARYKEY", SqliteNativeErrors.resultName(1555));
    assertEquals("SQLITE_CONSTRAINT_DATATYPE", SqliteNativeErrors.resultName(3091));
    assertEquals("SQLITE_CONSTRAINT_FOREIGNKEY", SqliteNativeErrors.resultName(787));
    assertEquals("SQLITE_CANTOPEN", SqliteNativeErrors.resultName(14));
    assertEquals("SQLITE_CANTOPEN_ISDIR", SqliteNativeErrors.resultName(526));
    assertEquals("SQLITE_NOTADB", SqliteNativeErrors.resultName(26));
    assertEquals("SQLITE_999999", SqliteNativeErrors.resultName(999999));
  }
}
