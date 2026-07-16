package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

/** Proves native-backup failures retain the SQLite result contract at each bridge boundary. */
class SqliteNativeBackupsTest extends SqliteNativeBridgeTestSupport {
  @Test
  void copyMainDatabase_rejectsInitializationFailureUsingDestinationDiagnostics() {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApi(MemorySegment.NULL, SqliteNativeResultCode.code("DONE"), 0);

      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeBackups.copyMainDatabase(
                      database(arena, sqliteApi), database(arena, sqliteApi)));

      assertEquals("SQLITE_OK", exception.getMessage());
    }
  }

  @Test
  void copyMainDatabase_rejectsCopyStepFailureAndStillFinishesTheNativeBackup() {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApi(
              arena.allocate(1),
              SqliteNativeResultCode.code("ERROR"),
              SqliteNativeResultCode.code("OK"));

      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeBackups.copyMainDatabase(
                      database(arena, sqliteApi), database(arena, sqliteApi)));

      assertEquals("SQLITE_ERROR", exception.getMessage());
    }
  }

  @Test
  void copyMainDatabase_rejectsNativeBackupFinishFailure() {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApi(
              arena.allocate(1),
              SqliteNativeResultCode.code("DONE"),
              SqliteNativeResultCode.code("ERROR"));

      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeBackups.copyMainDatabase(
                      database(arena, sqliteApi), database(arena, sqliteApi)));

      assertEquals("SQLITE_ERROR", exception.getMessage());
    }
  }

  private static SqliteNativeApi sqliteApi(
      MemorySegment backupHandle, int backupStepResult, int backupFinishResult) {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[SQLITE_API_ARGUMENT_BACKUP_INIT] =
        constantMethodHandle(
            backupHandle,
            MemorySegment.class,
            MemorySegment.class,
            MemorySegment.class,
            MemorySegment.class);
    sqliteApiArguments[SQLITE_API_ARGUMENT_BACKUP_STEP] =
        constantMethodHandle(backupStepResult, MemorySegment.class, int.class);
    sqliteApiArguments[SQLITE_API_ARGUMENT_BACKUP_FINISH] =
        constantMethodHandle(backupFinishResult, MemorySegment.class);
    return buildSqliteApi(sqliteApiArguments);
  }

  private static SqliteNativeDatabase database(Arena arena, SqliteNativeApi sqliteApi) {
    return new SqliteNativeDatabase(arena.allocate(1), sqliteApi);
  }
}
