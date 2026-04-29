package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Error/result-code/string helpers for the SQLite FFM bridge. */
final class SqliteNativeErrors {
  private static final Map<Integer, String> RESULT_NAMES =
      Map.ofEntries(
          Map.entry(SqliteNativeResultCodes.OK, "SQLITE_OK"),
          Map.entry(SqliteNativeResultCodes.ERROR, "SQLITE_ERROR"),
          Map.entry(SqliteNativeResultCodes.INTERNAL, "SQLITE_INTERNAL"),
          Map.entry(SqliteNativeResultCodes.PERM, "SQLITE_PERM"),
          Map.entry(SqliteNativeResultCodes.ABORT, "SQLITE_ABORT"),
          Map.entry(SqliteNativeResultCodes.ABORT_ROLLBACK, "SQLITE_ABORT_ROLLBACK"),
          Map.entry(SqliteNativeResultCodes.BUSY, "SQLITE_BUSY"),
          Map.entry(SqliteNativeResultCodes.BUSY_RECOVERY, "SQLITE_BUSY_RECOVERY"),
          Map.entry(SqliteNativeResultCodes.BUSY_SNAPSHOT, "SQLITE_BUSY_SNAPSHOT"),
          Map.entry(SqliteNativeResultCodes.BUSY_TIMEOUT, "SQLITE_BUSY_TIMEOUT"),
          Map.entry(SqliteNativeResultCodes.LOCKED, "SQLITE_LOCKED"),
          Map.entry(SqliteNativeResultCodes.LOCKED_SHAREDCACHE, "SQLITE_LOCKED_SHAREDCACHE"),
          Map.entry(SqliteNativeResultCodes.LOCKED_VTAB, "SQLITE_LOCKED_VTAB"),
          Map.entry(SqliteNativeResultCodes.NOMEM, "SQLITE_NOMEM"),
          Map.entry(SqliteNativeResultCodes.READONLY, "SQLITE_READONLY"),
          Map.entry(SqliteNativeResultCodes.READONLY_RECOVERY, "SQLITE_READONLY_RECOVERY"),
          Map.entry(SqliteNativeResultCodes.READONLY_CANTLOCK, "SQLITE_READONLY_CANTLOCK"),
          Map.entry(SqliteNativeResultCodes.READONLY_ROLLBACK, "SQLITE_READONLY_ROLLBACK"),
          Map.entry(SqliteNativeResultCodes.READONLY_DBMOVED, "SQLITE_READONLY_DBMOVED"),
          Map.entry(SqliteNativeResultCodes.READONLY_CANTINIT, "SQLITE_READONLY_CANTINIT"),
          Map.entry(SqliteNativeResultCodes.READONLY_DIRECTORY, "SQLITE_READONLY_DIRECTORY"),
          Map.entry(SqliteNativeResultCodes.INTERRUPT, "SQLITE_INTERRUPT"),
          Map.entry(SqliteNativeResultCodes.IOERR, "SQLITE_IOERR"),
          Map.entry(SqliteNativeResultCodes.IOERR_READ, "SQLITE_IOERR_READ"),
          Map.entry(SqliteNativeResultCodes.IOERR_SHORT_READ, "SQLITE_IOERR_SHORT_READ"),
          Map.entry(SqliteNativeResultCodes.IOERR_WRITE, "SQLITE_IOERR_WRITE"),
          Map.entry(SqliteNativeResultCodes.IOERR_FSYNC, "SQLITE_IOERR_FSYNC"),
          Map.entry(SqliteNativeResultCodes.IOERR_DIR_FSYNC, "SQLITE_IOERR_DIR_FSYNC"),
          Map.entry(SqliteNativeResultCodes.IOERR_TRUNCATE, "SQLITE_IOERR_TRUNCATE"),
          Map.entry(SqliteNativeResultCodes.IOERR_FSTAT, "SQLITE_IOERR_FSTAT"),
          Map.entry(SqliteNativeResultCodes.IOERR_UNLOCK, "SQLITE_IOERR_UNLOCK"),
          Map.entry(SqliteNativeResultCodes.IOERR_RDLOCK, "SQLITE_IOERR_RDLOCK"),
          Map.entry(SqliteNativeResultCodes.IOERR_DELETE, "SQLITE_IOERR_DELETE"),
          Map.entry(SqliteNativeResultCodes.IOERR_BLOCKED, "SQLITE_IOERR_BLOCKED"),
          Map.entry(SqliteNativeResultCodes.IOERR_NOMEM, "SQLITE_IOERR_NOMEM"),
          Map.entry(SqliteNativeResultCodes.IOERR_ACCESS, "SQLITE_IOERR_ACCESS"),
          Map.entry(
              SqliteNativeResultCodes.IOERR_CHECKRESERVEDLOCK, "SQLITE_IOERR_CHECKRESERVEDLOCK"),
          Map.entry(SqliteNativeResultCodes.IOERR_LOCK, "SQLITE_IOERR_LOCK"),
          Map.entry(SqliteNativeResultCodes.IOERR_CLOSE, "SQLITE_IOERR_CLOSE"),
          Map.entry(SqliteNativeResultCodes.IOERR_DIR_CLOSE, "SQLITE_IOERR_DIR_CLOSE"),
          Map.entry(SqliteNativeResultCodes.IOERR_SHMOPEN, "SQLITE_IOERR_SHMOPEN"),
          Map.entry(SqliteNativeResultCodes.IOERR_SHMSIZE, "SQLITE_IOERR_SHMSIZE"),
          Map.entry(SqliteNativeResultCodes.IOERR_SHMLOCK, "SQLITE_IOERR_SHMLOCK"),
          Map.entry(SqliteNativeResultCodes.IOERR_SHMMAP, "SQLITE_IOERR_SHMMAP"),
          Map.entry(SqliteNativeResultCodes.IOERR_SEEK, "SQLITE_IOERR_SEEK"),
          Map.entry(SqliteNativeResultCodes.IOERR_DELETE_NOENT, "SQLITE_IOERR_DELETE_NOENT"),
          Map.entry(SqliteNativeResultCodes.IOERR_MMAP, "SQLITE_IOERR_MMAP"),
          Map.entry(SqliteNativeResultCodes.IOERR_GETTEMPPATH, "SQLITE_IOERR_GETTEMPPATH"),
          Map.entry(SqliteNativeResultCodes.IOERR_CONVPATH, "SQLITE_IOERR_CONVPATH"),
          Map.entry(SqliteNativeResultCodes.IOERR_VNODE, "SQLITE_IOERR_VNODE"),
          Map.entry(SqliteNativeResultCodes.IOERR_AUTH, "SQLITE_IOERR_AUTH"),
          Map.entry(SqliteNativeResultCodes.IOERR_BEGIN_ATOMIC, "SQLITE_IOERR_BEGIN_ATOMIC"),
          Map.entry(SqliteNativeResultCodes.IOERR_COMMIT_ATOMIC, "SQLITE_IOERR_COMMIT_ATOMIC"),
          Map.entry(SqliteNativeResultCodes.IOERR_ROLLBACK_ATOMIC, "SQLITE_IOERR_ROLLBACK_ATOMIC"),
          Map.entry(SqliteNativeResultCodes.IOERR_DATA, "SQLITE_IOERR_DATA"),
          Map.entry(SqliteNativeResultCodes.IOERR_CORRUPTFS, "SQLITE_IOERR_CORRUPTFS"),
          Map.entry(SqliteNativeResultCodes.IOERR_IN_PAGE, "SQLITE_IOERR_IN_PAGE"),
          Map.entry(SqliteNativeResultCodes.IOERR_BADKEY, "SQLITE_IOERR_BADKEY"),
          Map.entry(SqliteNativeResultCodes.IOERR_CODEC, "SQLITE_IOERR_CODEC"),
          Map.entry(SqliteNativeResultCodes.CORRUPT, "SQLITE_CORRUPT"),
          Map.entry(SqliteNativeResultCodes.CORRUPT_VTAB, "SQLITE_CORRUPT_VTAB"),
          Map.entry(SqliteNativeResultCodes.CORRUPT_SEQUENCE, "SQLITE_CORRUPT_SEQUENCE"),
          Map.entry(SqliteNativeResultCodes.CORRUPT_INDEX, "SQLITE_CORRUPT_INDEX"),
          Map.entry(SqliteNativeResultCodes.NOTFOUND, "SQLITE_NOTFOUND"),
          Map.entry(SqliteNativeResultCodes.FULL, "SQLITE_FULL"),
          Map.entry(SqliteNativeResultCodes.PROTOCOL, "SQLITE_PROTOCOL"),
          Map.entry(SqliteNativeResultCodes.EMPTY, "SQLITE_EMPTY"),
          Map.entry(SqliteNativeResultCodes.SCHEMA, "SQLITE_SCHEMA"),
          Map.entry(SqliteNativeResultCodes.TOOBIG, "SQLITE_TOOBIG"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT, "SQLITE_CONSTRAINT"),
          Map.entry(SqliteNativeResultCodes.MISMATCH, "SQLITE_MISMATCH"),
          Map.entry(SqliteNativeResultCodes.MISUSE, "SQLITE_MISUSE"),
          Map.entry(SqliteNativeResultCodes.NOLFS, "SQLITE_NOLFS"),
          Map.entry(SqliteNativeResultCodes.AUTH, "SQLITE_AUTH"),
          Map.entry(SqliteNativeResultCodes.FORMAT, "SQLITE_FORMAT"),
          Map.entry(SqliteNativeResultCodes.RANGE, "SQLITE_RANGE"),
          Map.entry(SqliteNativeResultCodes.NOTADB, "SQLITE_NOTADB"),
          Map.entry(SqliteNativeResultCodes.NOTICE, "SQLITE_NOTICE"),
          Map.entry(SqliteNativeResultCodes.NOTICE_RECOVER_WAL, "SQLITE_NOTICE_RECOVER_WAL"),
          Map.entry(
              SqliteNativeResultCodes.NOTICE_RECOVER_ROLLBACK, "SQLITE_NOTICE_RECOVER_ROLLBACK"),
          Map.entry(SqliteNativeResultCodes.NOTICE_RBU, "SQLITE_NOTICE_RBU"),
          Map.entry(SqliteNativeResultCodes.WARNING, "SQLITE_WARNING"),
          Map.entry(SqliteNativeResultCodes.WARNING_AUTOINDEX, "SQLITE_WARNING_AUTOINDEX"),
          Map.entry(SqliteNativeResultCodes.ROW, "SQLITE_ROW"),
          Map.entry(SqliteNativeResultCodes.DONE, "SQLITE_DONE"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_CHECK, "SQLITE_CONSTRAINT_CHECK"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_COMMITHOOK, "SQLITE_CONSTRAINT_COMMITHOOK"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_NOTNULL, "SQLITE_CONSTRAINT_NOTNULL"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_UNIQUE, "SQLITE_CONSTRAINT_UNIQUE"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_PRIMARYKEY, "SQLITE_CONSTRAINT_PRIMARYKEY"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_TRIGGER, "SQLITE_CONSTRAINT_TRIGGER"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_VTAB, "SQLITE_CONSTRAINT_VTAB"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_FUNCTION, "SQLITE_CONSTRAINT_FUNCTION"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_ROWID, "SQLITE_CONSTRAINT_ROWID"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_PINNED, "SQLITE_CONSTRAINT_PINNED"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_DATATYPE, "SQLITE_CONSTRAINT_DATATYPE"),
          Map.entry(SqliteNativeResultCodes.CONSTRAINT_FOREIGNKEY, "SQLITE_CONSTRAINT_FOREIGNKEY"),
          Map.entry(SqliteNativeResultCodes.CANTOPEN, "SQLITE_CANTOPEN"),
          Map.entry(SqliteNativeResultCodes.CANTOPEN_NOTEMPDIR, "SQLITE_CANTOPEN_NOTEMPDIR"),
          Map.entry(SqliteNativeResultCodes.CANTOPEN_ISDIR, "SQLITE_CANTOPEN_ISDIR"),
          Map.entry(SqliteNativeResultCodes.CANTOPEN_FULLPATH, "SQLITE_CANTOPEN_FULLPATH"),
          Map.entry(SqliteNativeResultCodes.CANTOPEN_CONVPATH, "SQLITE_CANTOPEN_CONVPATH"),
          Map.entry(SqliteNativeResultCodes.CANTOPEN_DIRTYWAL, "SQLITE_CANTOPEN_DIRTYWAL"),
          Map.entry(SqliteNativeResultCodes.CANTOPEN_SYMLINK, "SQLITE_CANTOPEN_SYMLINK"));

  private SqliteNativeErrors() {}

  static SqliteNativeException failure(int resultCode, SqliteNativeApi sqliteApi) {
    String resultName = resultName(resultCode);
    String errorString =
        errorString(resultCode, sqliteApi.sqlite3Errstr(), SqliteNativeBootstrap.strlen());
    String message = errorString.equals(resultName) ? resultName : resultName + ": " + errorString;
    return new SqliteNativeException(resultCode, message);
  }

  static String errorMessage(@Nullable MemorySegment databaseHandle) {
    if (databaseHandle == null || databaseHandle.equals(MemorySegment.NULL)) {
      return "SQLite native failure.";
    }
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    return errorMessage(databaseHandle, sqliteApi.sqlite3Errmsg(), SqliteNativeBootstrap.strlen());
  }

  static String errorMessage(
      @Nullable MemorySegment databaseHandle, MethodHandle errorMessageHandle) {
    return errorMessage(databaseHandle, errorMessageHandle, SqliteNativeBootstrap.strlen());
  }

  static String errorMessage(
      @Nullable MemorySegment databaseHandle,
      MethodHandle errorMessageHandle,
      MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite error message.",
        () -> {
          if (databaseHandle == null || databaseHandle.equals(MemorySegment.NULL)) {
            return "SQLite native failure.";
          }
          MemorySegment errorMessagePointer =
              SqliteNativeCalls.addressToAddress(errorMessageHandle).invoke(databaseHandle);
          if (errorMessagePointer.equals(MemorySegment.NULL)) {
            return "SQLite native failure.";
          }
          return cString(errorMessagePointer, strlenHandle);
        });
  }

  static String scriptErrorMessage(MemorySegment databaseHandle, MemorySegment execErrorPointer) {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    return scriptErrorMessage(
        databaseHandle,
        execErrorPointer,
        sqliteApi.sqlite3Errmsg(),
        SqliteNativeBootstrap.strlen());
  }

  static String errorString(int resultCode) {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    return errorString(resultCode, sqliteApi.sqlite3Errstr(), SqliteNativeBootstrap.strlen());
  }

  static String errorString(
      int resultCode, MethodHandle errorStringHandle, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite error string.",
        () -> {
          MemorySegment errorStringPointer =
              SqliteNativeCalls.intToAddress(errorStringHandle).invoke(resultCode);
          if (errorStringPointer == null || errorStringPointer.equals(MemorySegment.NULL)) {
            return resultName(resultCode);
          }
          String errorString = cString(errorStringPointer, strlenHandle);
          return errorString.isBlank() ? resultName(resultCode) : errorString;
        });
  }

  static String scriptErrorMessage(
      int resultCode,
      @Nullable MemorySegment execErrorPointer,
      MethodHandle errorStringHandle,
      MethodHandle strlenHandle) {
    if (execErrorPointer != null && !execErrorPointer.equals(MemorySegment.NULL)) {
      return cString(execErrorPointer, strlenHandle);
    }
    return errorString(resultCode, errorStringHandle, strlenHandle);
  }

  static String scriptErrorMessage(
      MemorySegment databaseHandle,
      @Nullable MemorySegment execErrorPointer,
      MethodHandle errorMessageHandle,
      MethodHandle strlenHandle) {
    if (execErrorPointer != null && !execErrorPointer.equals(MemorySegment.NULL)) {
      return cString(execErrorPointer, strlenHandle);
    }
    return errorMessage(databaseHandle, errorMessageHandle, strlenHandle);
  }

  static String resultName(int resultCode) {
    return RESULT_NAMES.getOrDefault(resultCode, "SQLITE_" + resultCode);
  }

  static void freeSqliteBuffer(MemorySegment pointer, MethodHandle freeHandle) {
    if (pointer == null || pointer.equals(MemorySegment.NULL)) {
      return;
    }
    SqliteNativeInvocation.run(
        "Failed to free a SQLite-owned native buffer.",
        () -> {
          SqliteNativeCalls.addressToVoid(freeHandle).invoke(pointer);
        });
  }

  static String cString(MemorySegment cStringPointer, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read a native C string.",
        () -> {
          long byteLength = SqliteNativeCalls.addressToLong(strlenHandle).invoke(cStringPointer);
          return cStringPointer.reinterpret(byteLength + 1L).getString(0);
        });
  }
}
