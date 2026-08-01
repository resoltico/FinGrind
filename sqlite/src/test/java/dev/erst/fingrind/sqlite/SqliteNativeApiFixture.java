package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/** Native SQLite API fixture assembly helpers for bridge and failure tests. */
final class SqliteNativeApiFixture {
  private SqliteNativeApiFixture() {}

  static SqliteNativeApi sqliteApi(
      MethodHandle keyHandle,
      MethodHandle closeHandle,
      MethodHandle errorMessageHandle,
      MethodHandle errorStringHandle,
      MethodHandle extendedErrcodeHandle)
      throws ReflectiveOperationException {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[SqliteNativeBridgeTestSupport.SQLITE_API_ARGUMENT_CLOSE_V2] = closeHandle;
    sqliteApiArguments[SqliteNativeBridgeTestSupport.SQLITE_API_ARGUMENT_KEY] = keyHandle;
    sqliteApiArguments[SqliteNativeBridgeTestSupport.SQLITE_API_ARGUMENT_ERRMSG] =
        errorMessageHandle;
    sqliteApiArguments[SqliteNativeBridgeTestSupport.SQLITE_API_ARGUMENT_ERRSTR] =
        errorStringHandle;
    sqliteApiArguments[SqliteNativeBridgeTestSupport.SQLITE_API_ARGUMENT_EXTENDED_ERRCODE] =
        extendedErrcodeHandle;
    return buildSqliteApi(sqliteApiArguments);
  }

  static SqliteNativeApi sqliteApi(
      MethodHandle closeHandle,
      MethodHandle errorMessageHandle,
      MethodHandle errorStringHandle,
      MethodHandle extendedErrcodeHandle)
      throws ReflectiveOperationException {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[SqliteNativeBridgeTestSupport.SQLITE_API_ARGUMENT_CLOSE_V2] = closeHandle;
    sqliteApiArguments[SqliteNativeBridgeTestSupport.SQLITE_API_ARGUMENT_ERRMSG] =
        errorMessageHandle;
    sqliteApiArguments[SqliteNativeBridgeTestSupport.SQLITE_API_ARGUMENT_ERRSTR] =
        errorStringHandle;
    sqliteApiArguments[SqliteNativeBridgeTestSupport.SQLITE_API_ARGUMENT_EXTENDED_ERRCODE] =
        extendedErrcodeHandle;
    return buildSqliteApi(sqliteApiArguments);
  }

  static Object[] defaultSqliteApiArguments() {
    return new Object[] {
      Arena.ofShared(),
      SqliteNativeHandleFixtures.constantMethodHandle(
          0, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class),
      SqliteNativeHandleFixtures.constantMethodHandle(
          0, MemorySegment.class, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(
          0, MemorySegment.class, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(
          0, MemorySegment.class, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(
          0, MemorySegment.class, MemorySegment.class, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(MemorySegment.NULL, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(
          0, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class),
      SqliteNativeHandleFixtures.constantMethodHandle(
          0,
          MemorySegment.class,
          MemorySegment.class,
          MemorySegment.class,
          MemorySegment.class,
          MemorySegment.class),
      SqliteNativeHandleFixtures.voidMethodHandle(MemorySegment.class),
      SqliteNativeHandleFixtures.constantMethodHandle(
          0,
          MemorySegment.class,
          MemorySegment.class,
          int.class,
          MemorySegment.class,
          MemorySegment.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class, int.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(
          0L, MemorySegment.class, int.class, long.class),
      SqliteNativeHandleFixtures.constantMethodHandle(
          0, MemorySegment.class, int.class, MemorySegment.class, int.class, MemorySegment.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class),
      SqliteNativeHandleFixtures.constantMethodHandle(
          MemorySegment.NULL, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0L, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(MemorySegment.NULL, MemorySegment.class),
      SqliteNativeHandleFixtures.constantMethodHandle(MemorySegment.NULL, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class),
      "3.53.4",
      "2.4.0",
      SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
      SqliteRuntimeProvenance.BUNDLE_MANAGED,
      "/tmp/libsqlite3.dylib",
      SqliteNativeHandleFixtures.constantMethodHandle(
          MemorySegment.NULL,
          MemorySegment.class,
          MemorySegment.class,
          MemorySegment.class,
          MemorySegment.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class, int.class),
      SqliteNativeHandleFixtures.constantMethodHandle(0, MemorySegment.class)
    };
  }

  static SqliteNativeApi buildSqliteApi(Object[] sqliteApiArguments) {
    return new SqliteNativeApi(
        (Arena) sqliteApiArguments[0],
        (MethodHandle) sqliteApiArguments[1],
        (MethodHandle) sqliteApiArguments[2],
        (MethodHandle) sqliteApiArguments[3],
        (MethodHandle) sqliteApiArguments[4],
        (MethodHandle) sqliteApiArguments[5],
        (MethodHandle) sqliteApiArguments[6],
        (MethodHandle) sqliteApiArguments[7],
        (MethodHandle) sqliteApiArguments[8],
        (MethodHandle) sqliteApiArguments[9],
        (MethodHandle) sqliteApiArguments[10],
        (MethodHandle) sqliteApiArguments[11],
        (MethodHandle) sqliteApiArguments[12],
        (MethodHandle) sqliteApiArguments[13],
        (MethodHandle) sqliteApiArguments[14],
        (MethodHandle) sqliteApiArguments[15],
        (MethodHandle) sqliteApiArguments[16],
        (MethodHandle) sqliteApiArguments[17],
        (MethodHandle) sqliteApiArguments[18],
        (MethodHandle) sqliteApiArguments[19],
        (MethodHandle) sqliteApiArguments[20],
        (MethodHandle) sqliteApiArguments[21],
        (MethodHandle) sqliteApiArguments[22],
        (MethodHandle) sqliteApiArguments[23],
        (MethodHandle) sqliteApiArguments[24],
        (MethodHandle) sqliteApiArguments[25],
        (MethodHandle) sqliteApiArguments[26],
        (MethodHandle) sqliteApiArguments[27],
        (String) sqliteApiArguments[28],
        (String) sqliteApiArguments[29],
        (String) sqliteApiArguments[30],
        (SqliteRuntimeProvenance) sqliteApiArguments[31],
        (String) sqliteApiArguments[32],
        (MethodHandle) sqliteApiArguments[33],
        (MethodHandle) sqliteApiArguments[34],
        (MethodHandle) sqliteApiArguments[35]);
  }
}
