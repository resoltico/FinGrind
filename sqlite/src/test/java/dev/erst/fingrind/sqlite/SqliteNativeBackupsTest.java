package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
              SqliteNativeResultCode.code("OK"),
              SqliteNativeResultCode.code("CANTOPEN"));

      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeBackups.copyMainDatabase(
                      database(arena, sqliteApi), database(arena, sqliteApi)));

      assertEquals("SQLITE_CANTOPEN", exception.getMessage());
    }
  }

  @Test
  void copyMainDatabase_retriesTransientLockFailureOnTheSameNativeBackup() {
    try (Arena arena = Arena.ofConfined()) {
      SequentialBackupStepResult backupStepResult =
          new SequentialBackupStepResult(
              SqliteNativeResultCode.code("BUSY"), SqliteNativeResultCode.code("DONE"));
      SqliteNativeApi sqliteApi =
          sqliteApi(
              arena.allocate(1),
              sequentialBackupStepHandle(backupStepResult),
              SqliteNativeResultCode.code("OK"),
              SqliteNativeResultCode.code("OK"));

      SqliteNativeBackups.copyMainDatabase(database(arena, sqliteApi), database(arena, sqliteApi));

      assertEquals(2, backupStepResult.calls());
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
    return sqliteApi(
        backupHandle, backupStepResult, backupFinishResult, SqliteNativeResultCode.code("OK"));
  }

  private static SqliteNativeApi sqliteApi(
      MemorySegment backupHandle,
      int backupStepResult,
      int backupFinishResult,
      int destinationExtendedResultCode) {
    return sqliteApi(
        backupHandle,
        constantMethodHandle(backupStepResult, MemorySegment.class, int.class),
        backupFinishResult,
        destinationExtendedResultCode);
  }

  private static SqliteNativeApi sqliteApi(
      MemorySegment backupHandle,
      MethodHandle backupStepHandle,
      int backupFinishResult,
      int destinationExtendedResultCode) {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[SQLITE_API_ARGUMENT_BACKUP_INIT] =
        constantMethodHandle(
            backupHandle,
            MemorySegment.class,
            MemorySegment.class,
            MemorySegment.class,
            MemorySegment.class);
    sqliteApiArguments[SQLITE_API_ARGUMENT_BACKUP_STEP] = backupStepHandle;
    sqliteApiArguments[SQLITE_API_ARGUMENT_BACKUP_FINISH] =
        constantMethodHandle(backupFinishResult, MemorySegment.class);
    sqliteApiArguments[SQLITE_API_ARGUMENT_EXTENDED_ERRCODE] =
        constantMethodHandle(destinationExtendedResultCode, MemorySegment.class);
    return buildSqliteApi(sqliteApiArguments);
  }

  private static MethodHandle sequentialBackupStepHandle(SequentialBackupStepResult result) {
    try {
      return MethodHandles.dropArguments(
          MethodHandles.lookup()
              .findVirtual(
                  SequentialBackupStepResult.class, "next", MethodType.methodType(int.class))
              .bindTo(result),
          0,
          MemorySegment.class,
          int.class);
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError(
          "Failed to build the sequential SQLite backup-step test handle.", exception);
    }
  }

  /** Supplies deterministic native backup-step outcomes for retry-path coverage. */
  static final class SequentialBackupStepResult {
    private final int[] resultCodes;
    private int callCount;

    private SequentialBackupStepResult(int... resultCodes) {
      this.resultCodes = resultCodes;
    }

    int next() {
      int resultCode = resultCodes[callCount];
      callCount++;
      return resultCode;
    }

    int calls() {
      return callCount;
    }
  }

  private static SqliteNativeDatabase database(Arena arena, SqliteNativeApi sqliteApi) {
    return new SqliteNativeDatabase(arena.allocate(1), sqliteApi);
  }
}
