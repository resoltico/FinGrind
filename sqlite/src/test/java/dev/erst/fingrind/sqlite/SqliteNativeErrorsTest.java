package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests the stable names assigned to SQLite result and extended-result codes. */
class SqliteNativeErrorsTest {

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
}
