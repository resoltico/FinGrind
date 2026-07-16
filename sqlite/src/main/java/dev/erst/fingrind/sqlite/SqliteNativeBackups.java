package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeBackupInitCall;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/** Copies one keyed main database into another through SQLite's native backup API. */
final class SqliteNativeBackups {
  private static final String MAIN_DATABASE_NAME = "main";
  private static final int COPY_ALL_REMAINING_PAGES = -1;

  private SqliteNativeBackups() {}

  static void copyMainDatabase(
      SqliteNativeDatabase sourceDatabase, SqliteNativeDatabase destinationDatabase) {
    Objects.requireNonNull(sourceDatabase, "sourceDatabase");
    Objects.requireNonNull(destinationDatabase, "destinationDatabase");
    SqliteNativeApi sqliteApi = destinationDatabase.sqliteApi();
    try (Arena arena = Arena.ofConfined();
        NativeBackup backup =
            NativeBackup.initialize(sourceDatabase, destinationDatabase, sqliteApi, arena)) {
      backup.copyAllRemainingPages();
    }
  }

  /** Owns one initialized native backup handle so try-with-resources closes it exactly once. */
  private static final class NativeBackup implements AutoCloseable {
    private final MemorySegment backupHandle;
    private final SqliteNativeApi sqliteApi;

    private NativeBackup(MemorySegment backupHandle, SqliteNativeApi sqliteApi) {
      this.backupHandle = backupHandle;
      this.sqliteApi = sqliteApi;
    }

    static NativeBackup initialize(
        SqliteNativeDatabase sourceDatabase,
        SqliteNativeDatabase destinationDatabase,
        SqliteNativeApi sqliteApi,
        Arena arena) {
      MemorySegment backupHandle =
          SqliteNativeInvocation.invokeSqlite(
              "Failed to initialize a SQLite native backup.",
              () ->
                  SqliteNativeCallAdapter.adapt(
                          SqliteNativeBackupInitCall.class, sqliteApi.sqlite3BackupInit())
                      .invoke(
                          destinationDatabase.handle(),
                          arena.allocateFrom(MAIN_DATABASE_NAME),
                          sourceDatabase.handle(),
                          arena.allocateFrom(MAIN_DATABASE_NAME)));
      if (backupHandle.equals(MemorySegment.NULL)) {
        throw destinationFailure(destinationDatabase, sqliteApi);
      }
      return new NativeBackup(backupHandle, sqliteApi);
    }

    void copyAllRemainingPages() {
      SqliteStoreOperations.retryTransientLockFailures(
          () -> {
            int resultCode =
                SqliteNativeInvocation.invokeSqlite(
                    "Failed to copy a SQLite native backup.",
                    () ->
                        SqliteNativeCallAdapter.adapt(
                                SqliteNativeCalls.AddressIntToIntCall.class,
                                sqliteApi.sqlite3BackupStep())
                            .invoke(backupHandle, COPY_ALL_REMAINING_PAGES));
            if (resultCode != SqliteNativeResultCode.code("DONE")) {
              throw SqliteNativeErrors.failure(resultCode, sqliteApi);
            }
            return Boolean.TRUE;
          });
    }

    @Override
    public void close() {
      int resultCode =
          SqliteNativeInvocation.invokeSqlite(
              "Failed to finish a SQLite native backup.",
              () ->
                  SqliteNativeCallAdapter.adapt(
                          SqliteNativeCalls.AddressToIntCall.class, sqliteApi.sqlite3BackupFinish())
                      .invoke(backupHandle));
      if (resultCode != SqliteNativeResultCode.code("OK")) {
        throw SqliteNativeErrors.failure(resultCode, sqliteApi);
      }
    }
  }

  private static SqliteNativeException destinationFailure(
      SqliteNativeDatabase destinationDatabase, SqliteNativeApi sqliteApi) {
    return SqliteNativeErrors.failure(
        SqliteNativeStatements.extendedErrorCode(destinationDatabase.handle(), sqliteApi),
        sqliteApi);
  }
}
