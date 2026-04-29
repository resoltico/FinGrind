package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.util.List;
import java.util.function.Supplier;
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
        List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
        SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS);
    assertEquals("3.53.0", SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION);
    assertEquals("2.3.3", SqliteRuntime.REQUIRED_SQLITE3MC_VERSION);
    assertEquals(
        "2026-04-09 11:41:38 4525003a53a7fc63ca75c59b22c79608659ca12f0131f52c18637f829977f20b",
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.requiredSqliteSourceId());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.VERIFIED, runtimeProbe.compileOptionsVerification());
    assertEquals(SqliteRuntime.Status.READY, runtimeProbe.status());
    assertEquals(SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED, runtimeProbe.runtimeProvenance());
    assertFalse(runtimeProbe.loadedLibraryPath().isBlank());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.loadedSqliteSourceId());
    assertFalse(SqliteRuntime.sqliteVersion().isBlank());
    assertEquals("2.3.3", SqliteRuntime.sqlite3MultipleCiphersVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, SqliteRuntime.sqliteSourceId());
    assertEquals(SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED, SqliteRuntime.runtimeProvenance());
    assertFalse(SqliteRuntime.loadedLibraryPath().isBlank());
  }

  @Test
  void failureDetail_prefersMessageAndFallsBackToType() {
    assertEquals("boom", SqliteRuntime.failureDetail(new IllegalStateException("boom")));
    assertEquals("RuntimeException", SqliteRuntime.failureDetail(new RuntimeException()));
  }

  @Test
  void sqliteRuntimeProbe_reportsUnavailableWhenConfiguredLibraryTargetIsInvalid() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probeConfiguredTarget(
            () -> {
              throw new IllegalStateException("bundle launcher misconfigured");
            });

    assertEquals(SqliteRuntime.Status.UNAVAILABLE, runtimeProbe.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertTrue(runtimeProbe.issue().contains("bundle launcher misconfigured"));
  }

  @Test
  void probe_reportsIncompatibleRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        probe(
            () -> {
              throw new UnsupportedSqliteVersionException(
                  "3.51.0",
                  "3.53.0",
                  "managed-only",
                  "2.3.3",
                  SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
            },
            () -> "2.3.3",
            () -> SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals(SqliteRuntimeProvenance.BUNDLE_MANAGED, runtimeProbe.runtimeProvenance());
    assertEquals("/tmp/libsqlite3.dylib", runtimeProbe.loadedLibraryPath());
    assertEquals("3.51.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.loadedSqliteSourceId());
    assertTrue(runtimeProbe.issue().contains("requires SQLite 3.53.0 or newer"));
  }

  @Test
  void probe_reportsUnavailableRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        probe(
            () -> {
              throw new IllegalStateException("sqlite runtime unavailable");
            },
            () -> "2.3.3",
            () -> SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertEquals(SqliteRuntime.Status.UNAVAILABLE, runtimeProbe.status());
    assertEquals("sqlite runtime unavailable", runtimeProbe.issue());
    assertNull(runtimeProbe.runtimeProvenance());
    assertNull(runtimeProbe.loadedLibraryPath());
    assertNull(runtimeProbe.loadedSqliteVersion());
    assertNull(runtimeProbe.loadedSqlite3mcVersion());
    assertNull(runtimeProbe.loadedSqliteSourceId());
  }

  @Test
  void probe_reportsIncompatibleSqlite3mcRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        probe(
            () -> "3.53.0",
            () -> {
              throw new UnsupportedSqliteMultipleCiphersVersionException(
                  "2.3.2",
                  "2.3.3",
                  "managed-only",
                  "3.53.0",
                  SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
            },
            () -> SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.2", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.loadedSqliteSourceId());
    assertTrue(runtimeProbe.issue().contains("requires SQLite3 Multiple Ciphers 2.3.3"));
  }

  @Test
  void probe_reportsIncompatibleSourceIdRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        probe(
            () -> "3.53.0",
            () -> "2.3.3",
            () -> {
              throw new UnsupportedSqliteSourceIdException(
                  "2026-04-09 wrong-source-id",
                  SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                  "managed-only",
                  "3.53.0",
                  "2.3.3");
            });

    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals("2026-04-09 wrong-source-id", runtimeProbe.loadedSqliteSourceId());
    assertTrue(runtimeProbe.issue().contains("requires SQLite source id"));
  }

  @Test
  void probe_reportsIncompatibleCompileOptionsRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        probe(
            () -> {
              throw new UnsupportedSqliteCompileOptionsException(
                  "3.53.0",
                  "2.3.3",
                  SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                  "managed-only",
                  List.of("SECURE_DELETE"));
            },
            () -> {
              throw new AssertionError("sqlite3mc version lookup should not run");
            },
            () -> SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.FAILED, runtimeProbe.compileOptionsVerification());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.loadedSqliteSourceId());
    assertTrue(runtimeProbe.issue().contains("missing required compile options"));
  }

  @Test
  void runtimeProbeAndStatusExposeStableValueSemantics() {
    SqliteRuntime.Probe runtimeProbe = readyProbe();

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.requiredSqliteSourceId());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.VERIFIED, runtimeProbe.compileOptionsVerification());
    assertEquals(SqliteRuntime.Status.READY, runtimeProbe.status());
    assertEquals(SqliteRuntimeProvenance.BUNDLE_MANAGED, runtimeProbe.runtimeProvenance());
    assertEquals("/tmp/libsqlite3.dylib", runtimeProbe.loadedLibraryPath());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.loadedSqliteSourceId());
    assertNull(runtimeProbe.issue());
    assertEquals(runtimeProbe, readyProbe());
    assertEquals(runtimeProbe.hashCode(), readyProbe().hashCode());
    assertTrue(runtimeProbe.toString().contains("managed-only"));
    assertEquals("ready", SqliteRuntime.Status.READY.wireValue());
    assertEquals("unavailable", SqliteRuntime.Status.UNAVAILABLE.wireValue());
    assertEquals("incompatible", SqliteRuntime.Status.INCOMPATIBLE.wireValue());
  }

  @Test
  void runtimeProbe_normalizesWhitespaceAroundRequiredAndOptionalFields() {
    SqliteRuntime.Probe runtimeProbe =
        new SqliteRuntime.Probe(
            "  managed-only  ",
            "  3.53.0  ",
            "  2.3.3  ",
            "  " + SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID + "  ",
            SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
            SqliteRuntime.Status.UNAVAILABLE,
            null,
            "   ",
            "\t",
            "\n",
            "  ",
            "  runtime unavailable  ");

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID, runtimeProbe.requiredSqliteSourceId());
    assertNull(runtimeProbe.loadedLibraryPath());
    assertNull(runtimeProbe.loadedSqliteVersion());
    assertNull(runtimeProbe.loadedSqlite3mcVersion());
    assertNull(runtimeProbe.loadedSqliteSourceId());
    assertEquals("runtime unavailable", runtimeProbe.issue());
  }

  @Test
  void runtimeProbe_rejectsInvalidStatusSpecificShapes() {
    assertThrows(
        NullPointerException.class,
        () -> runtimeProbe("managed-only", null, null, null, null, null, null, null, "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                null,
                "/tmp/libsqlite3.dylib",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                null,
                "3.53.0",
                "2.3.3",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                null,
                "2.3.3",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.0",
                null,
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.0",
                "2.3.3",
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.FAILED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.0",
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                "/tmp/libsqlite3.dylib",
                null,
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                "3.53.0",
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                "2.3.3",
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                null,
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.51.0",
                "2.3.3",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                null,
                "/tmp/libsqlite3.dylib",
                "3.51.0",
                "2.3.3",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                null,
                "3.51.0",
                "2.3.3",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                null,
                "2.3.3",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.53.0",
                null,
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.51.0",
                "2.3.3",
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                "managed-only",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.INCOMPATIBLE,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                "/tmp/libsqlite3.dylib",
                "3.51.0",
                "2.3.3",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtimeProbe(
                " ",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                "boom"));
  }

  @Test
  void resultName_mapsKnownAndUnknownCodes() {
    assertEquals("SQLITE_OK", SqliteNativeErrors.resultName(0));
    assertEquals("SQLITE_ERROR", SqliteNativeErrors.resultName(1));
    assertEquals("SQLITE_INTERNAL", SqliteNativeErrors.resultName(2));
    assertEquals("SQLITE_PERM", SqliteNativeErrors.resultName(3));
    assertEquals("SQLITE_ABORT", SqliteNativeErrors.resultName(4));
    assertEquals("SQLITE_ABORT_ROLLBACK", SqliteNativeErrors.resultName(516));
    assertEquals("SQLITE_BUSY", SqliteNativeErrors.resultName(5));
    assertEquals("SQLITE_BUSY_RECOVERY", SqliteNativeErrors.resultName(261));
    assertEquals("SQLITE_BUSY_SNAPSHOT", SqliteNativeErrors.resultName(517));
    assertEquals("SQLITE_BUSY_TIMEOUT", SqliteNativeErrors.resultName(773));
    assertEquals("SQLITE_LOCKED", SqliteNativeErrors.resultName(6));
    assertEquals("SQLITE_LOCKED_SHAREDCACHE", SqliteNativeErrors.resultName(262));
    assertEquals("SQLITE_LOCKED_VTAB", SqliteNativeErrors.resultName(518));
    assertEquals("SQLITE_NOMEM", SqliteNativeErrors.resultName(7));
    assertEquals("SQLITE_READONLY", SqliteNativeErrors.resultName(8));
    assertEquals("SQLITE_READONLY_RECOVERY", SqliteNativeErrors.resultName(264));
    assertEquals("SQLITE_READONLY_ROLLBACK", SqliteNativeErrors.resultName(776));
    assertEquals("SQLITE_READONLY_DIRECTORY", SqliteNativeErrors.resultName(1544));
    assertEquals("SQLITE_INTERRUPT", SqliteNativeErrors.resultName(9));
    assertEquals("SQLITE_IOERR", SqliteNativeErrors.resultName(10));
    assertEquals("SQLITE_IOERR_READ", SqliteNativeErrors.resultName(266));
    assertEquals("SQLITE_IOERR_CONVPATH", SqliteNativeErrors.resultName(6666));
    assertEquals("SQLITE_IOERR_CODEC", SqliteNativeErrors.resultName(9226));
    assertEquals("SQLITE_CORRUPT", SqliteNativeErrors.resultName(11));
    assertEquals("SQLITE_CORRUPT_SEQUENCE", SqliteNativeErrors.resultName(523));
    assertEquals("SQLITE_NOTFOUND", SqliteNativeErrors.resultName(12));
    assertEquals("SQLITE_FULL", SqliteNativeErrors.resultName(13));
    assertEquals("SQLITE_PROTOCOL", SqliteNativeErrors.resultName(15));
    assertEquals("SQLITE_SCHEMA", SqliteNativeErrors.resultName(17));
    assertEquals("SQLITE_CONSTRAINT", SqliteNativeErrors.resultName(19));
    assertEquals("SQLITE_CONSTRAINT_COMMITHOOK", SqliteNativeErrors.resultName(531));
    assertEquals("SQLITE_MISUSE", SqliteNativeErrors.resultName(21));
    assertEquals("SQLITE_RANGE", SqliteNativeErrors.resultName(25));
    assertEquals("SQLITE_ROW", SqliteNativeErrors.resultName(100));
    assertEquals("SQLITE_DONE", SqliteNativeErrors.resultName(101));
    assertEquals("SQLITE_CONSTRAINT_CHECK", SqliteNativeErrors.resultName(275));
    assertEquals("SQLITE_CONSTRAINT_NOTNULL", SqliteNativeErrors.resultName(1299));
    assertEquals("SQLITE_CONSTRAINT_UNIQUE", SqliteNativeErrors.resultName(2067));
    assertEquals("SQLITE_CONSTRAINT_PRIMARYKEY", SqliteNativeErrors.resultName(1555));
    assertEquals("SQLITE_CONSTRAINT_TRIGGER", SqliteNativeErrors.resultName(1811));
    assertEquals("SQLITE_CONSTRAINT_DATATYPE", SqliteNativeErrors.resultName(3091));
    assertEquals("SQLITE_CONSTRAINT_FOREIGNKEY", SqliteNativeErrors.resultName(787));
    assertEquals("SQLITE_CANTOPEN", SqliteNativeErrors.resultName(14));
    assertEquals("SQLITE_CANTOPEN_NOTEMPDIR", SqliteNativeErrors.resultName(270));
    assertEquals("SQLITE_CANTOPEN_ISDIR", SqliteNativeErrors.resultName(526));
    assertEquals("SQLITE_CANTOPEN_FULLPATH", SqliteNativeErrors.resultName(782));
    assertEquals("SQLITE_CANTOPEN_SYMLINK", SqliteNativeErrors.resultName(1550));
    assertEquals("SQLITE_NOTADB", SqliteNativeErrors.resultName(26));
    assertEquals("SQLITE_999999", SqliteNativeErrors.resultName(999999));
  }

  private static SqliteRuntime.Probe probe(
      Supplier<String> sqliteVersionSupplier,
      Supplier<String> sqlite3mcVersionSupplier,
      Supplier<String> sqliteSourceIdSupplier) {
    return SqliteRuntime.probe(
        () -> "managed-only",
        () -> SqliteRuntimeProvenance.BUNDLE_MANAGED,
        () -> "/tmp/libsqlite3.dylib",
        sqliteVersionSupplier,
        sqlite3mcVersionSupplier,
        sqliteSourceIdSupplier,
        SqliteRuntime::failureDetail);
  }

  private static SqliteRuntime.Probe readyProbe() {
    return runtimeProbe(
        "managed-only",
        SqliteCompileOptionsVerificationStatus.VERIFIED,
        SqliteRuntime.Status.READY,
        SqliteRuntimeProvenance.BUNDLE_MANAGED,
        "/tmp/libsqlite3.dylib",
        "3.53.0",
        "2.3.3",
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        null);
  }

  private static SqliteRuntime.Probe runtimeProbe(
      String libraryMode,
      SqliteCompileOptionsVerificationStatus compileOptionsVerification,
      SqliteRuntime.Status status,
      SqliteRuntimeProvenance runtimeProvenance,
      String loadedLibraryPath,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String loadedSqliteSourceId,
      String issue) {
    return new SqliteRuntime.Probe(
        libraryMode,
        "3.53.0",
        "2.3.3",
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        compileOptionsVerification,
        status,
        runtimeProvenance,
        loadedLibraryPath,
        loadedSqliteVersion,
        loadedSqlite3mcVersion,
        loadedSqliteSourceId,
        issue);
  }
}
