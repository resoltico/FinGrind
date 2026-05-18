package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** Shared native API fixtures and handle helpers for split SQLite bridge tests. */
class SqliteNativeBridgeTestSupport {
  static final String TEST_BOOK_KEY = "native-library-test-book-key";
  static final int SQLITE_API_ARGUMENT_OPEN_V2 = 1;
  static final int SQLITE_API_ARGUMENT_CLOSE_V2 = 2;
  static final int SQLITE_API_ARGUMENT_KEY = 3;
  static final int SQLITE_API_ARGUMENT_BUSY_TIMEOUT = 6;
  static final int SQLITE_API_ARGUMENT_EXTENDED_RESULT_CODES = 7;
  static final int SQLITE_API_ARGUMENT_EXEC = 12;
  static final int SQLITE_API_ARGUMENT_ERRMSG = 25;
  static final int SQLITE_API_ARGUMENT_ERRSTR = 26;
  static final int SQLITE_API_ARGUMENT_EXTENDED_ERRCODE = 27;
  static final int SQLITE_API_ARGUMENT_LOADED_VERSION = 28;
  static final int SQLITE_API_ARGUMENT_LOADED_SQLITE3MC_VERSION = 29;
  static final int SQLITE_API_ARGUMENT_LOADED_SOURCE_ID = 30;
  static final int SQLITE_API_ARGUMENT_LOADED_LIBRARY_PATH = 32;

  protected SqliteNativeBridgeTestSupport() {}

  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  static SqliteNativeApi sqliteApi(
      MethodHandle keyHandle,
      MethodHandle closeHandle,
      MethodHandle errorMessageHandle,
      MethodHandle errorStringHandle,
      MethodHandle extendedErrcodeHandle)
      throws ReflectiveOperationException {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] = closeHandle;
    sqliteApiArguments[SQLITE_API_ARGUMENT_KEY] = keyHandle;
    sqliteApiArguments[SQLITE_API_ARGUMENT_ERRMSG] = errorMessageHandle;
    sqliteApiArguments[SQLITE_API_ARGUMENT_ERRSTR] = errorStringHandle;
    sqliteApiArguments[SQLITE_API_ARGUMENT_EXTENDED_ERRCODE] = extendedErrcodeHandle;
    return buildSqliteApi(sqliteApiArguments);
  }

  static SqliteNativeApi sqliteApi(
      MethodHandle closeHandle,
      MethodHandle errorMessageHandle,
      MethodHandle errorStringHandle,
      MethodHandle extendedErrcodeHandle)
      throws ReflectiveOperationException {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] = closeHandle;
    sqliteApiArguments[SQLITE_API_ARGUMENT_ERRMSG] = errorMessageHandle;
    sqliteApiArguments[SQLITE_API_ARGUMENT_ERRSTR] = errorStringHandle;
    sqliteApiArguments[SQLITE_API_ARGUMENT_EXTENDED_ERRCODE] = extendedErrcodeHandle;
    return buildSqliteApi(sqliteApiArguments);
  }

  static Object[] defaultSqliteApiArguments() {
    return new Object[] {
      Arena.ofShared(),
      constantMethodHandle(
          0, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class),
      constantMethodHandle(0, MemorySegment.class),
      constantMethodHandle(0, MemorySegment.class, MemorySegment.class, int.class),
      constantMethodHandle(0, MemorySegment.class, MemorySegment.class, int.class),
      constantMethodHandle(0),
      constantMethodHandle(0, MemorySegment.class, int.class),
      constantMethodHandle(0, MemorySegment.class, int.class),
      constantMethodHandle(0, MemorySegment.class, MemorySegment.class, int.class),
      constantMethodHandle(
          0, MemorySegment.class, MemorySegment.class, MemorySegment.class, int.class),
      constantMethodHandle(MemorySegment.NULL, int.class),
      constantMethodHandle(
          0, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class),
      constantMethodHandle(
          0,
          MemorySegment.class,
          MemorySegment.class,
          MemorySegment.class,
          MemorySegment.class,
          MemorySegment.class),
      voidMethodHandle(MemorySegment.class),
      constantMethodHandle(
          0,
          MemorySegment.class,
          MemorySegment.class,
          int.class,
          MemorySegment.class,
          MemorySegment.class),
      constantMethodHandle(0, MemorySegment.class, int.class),
      constantMethodHandle(0, MemorySegment.class, int.class, int.class),
      constantMethodHandle(0L, MemorySegment.class, int.class, long.class),
      constantMethodHandle(
          0, MemorySegment.class, int.class, MemorySegment.class, int.class, MemorySegment.class),
      constantMethodHandle(0, MemorySegment.class),
      constantMethodHandle(0, MemorySegment.class),
      constantMethodHandle(MemorySegment.NULL, MemorySegment.class, int.class),
      constantMethodHandle(0, MemorySegment.class, int.class),
      constantMethodHandle(0, MemorySegment.class, int.class),
      constantMethodHandle(0L, MemorySegment.class, int.class),
      constantMethodHandle(MemorySegment.NULL, MemorySegment.class),
      constantMethodHandle(MemorySegment.NULL, int.class),
      constantMethodHandle(0, MemorySegment.class),
      "3.53.1",
      "2.3.4",
      SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
      SqliteRuntimeProvenance.BUNDLE_MANAGED,
      "/tmp/libsqlite3.dylib"
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
        (String) sqliteApiArguments[32]);
  }

  BookAccess bookAccess(Path bookPath) {
    return bookAccess(bookPath, TEST_BOOK_KEY);
  }

  BookAccess bookAccess(Path bookPath, String keyText) {
    try {
      Path keyPath = tempDirectory.resolve("book-keys").resolve(bookPath.getFileName() + ".key");
      writeSecureKeyFile(keyPath, keyText);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  static MethodHandle constantMethodHandle(Object value, Class<?>... parameterTypes) {
    MethodHandle constantHandle = MethodHandles.constant(constantType(value), value);
    return MethodHandles.dropArguments(constantHandle, 0, parameterTypes);
  }

  static MethodHandle throwingMethodHandle(
      Throwable throwable, Class<?> returnType, Class<?>... parameterTypes) {
    MethodHandle throwingHandle = MethodHandles.throwException(returnType, Throwable.class);
    return MethodHandles.dropArguments(
        MethodHandles.insertArguments(throwingHandle, 0, throwable), 0, parameterTypes);
  }

  static MethodHandle voidMethodHandle(Class<?>... parameterTypes) {
    return MethodHandles.dropArguments(
        MethodHandles.empty(java.lang.invoke.MethodType.methodType(void.class)), 0, parameterTypes);
  }

  static Class<?> constantType(Object value) {
    return switch (value) {
      case Integer _ -> int.class;
      case Long _ -> long.class;
      case MemorySegment _ -> MemorySegment.class;
      default -> value.getClass();
    };
  }

  static String expectedNativeLibraryFileName() {
    String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (operatingSystem.contains("mac")) {
      return "libsqlite3.dylib";
    }
    if (operatingSystem.contains("linux")) {
      return "libsqlite3.so.0";
    }
    if (operatingSystem.contains("windows")) {
      return "sqlite3.dll";
    }
    throw new IllegalStateException(
        "Unsupported test operating system: " + System.getProperty("os.name"));
  }

  static void restoreSystemProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
      return;
    }
    System.setProperty(key, value);
  }

  static int recordShutdownCall(AtomicInteger shutdownCalls) {
    return shutdownCalls.incrementAndGet();
  }

  static int recordCloseCall(AtomicInteger closeCalls, MemorySegment databaseHandle) {
    return closeCalls.incrementAndGet() == 1 && !databaseHandle.equals(MemorySegment.NULL) ? 0 : 14;
  }

  static int recordCloseCallThenThrow(AtomicInteger closeCalls, MemorySegment databaseHandle) {
    closeCalls.incrementAndGet();
    throw new IllegalStateException(
        "close boom for " + (databaseHandle.equals(MemorySegment.NULL) ? "null" : "handle"));
  }

  static int failThenSucceedCloseCall(AtomicInteger closeCalls, MemorySegment databaseHandle) {
    closeCalls.incrementAndGet();
    return closeCalls.get() == 1 ? 14 : 0;
  }

  static int failThenDelegateCloseCall(
      AtomicInteger closeCalls,
      SqliteNativeCalls.AddressToIntCall delegateClose,
      MemorySegment databaseHandle) {
    closeCalls.incrementAndGet();
    return closeCalls.get() == 1 ? 14 : delegateClose.invoke(databaseHandle);
  }

  static int throwIllegalStateThenSucceedCloseCall(
      AtomicInteger closeCalls, MemorySegment databaseHandle) {
    if (closeCalls.getAndIncrement() == 0) {
      throw new IllegalStateException("boom");
    }
    return 0;
  }

  static int throwIllegalStateThenDelegateCloseCall(
      AtomicInteger closeCalls,
      SqliteNativeCalls.AddressToIntCall delegateClose,
      MemorySegment databaseHandle) {
    if (closeCalls.getAndIncrement() == 0) {
      throw new IllegalStateException("boom");
    }
    return delegateClose.invoke(databaseHandle);
  }

  static int throwAssertionThenSucceedCloseCall(
      AtomicInteger closeCalls, MemorySegment databaseHandle) {
    if (closeCalls.getAndIncrement() == 0) {
      throw new AssertionError("boom");
    }
    return 0;
  }

  static int throwAssertionThenDelegateCloseCall(
      AtomicInteger closeCalls,
      SqliteNativeCalls.AddressToIntCall delegateClose,
      MemorySegment databaseHandle) {
    if (closeCalls.getAndIncrement() == 0) {
      throw new AssertionError("boom");
    }
    return delegateClose.invoke(databaseHandle);
  }

  static int openWithDatabaseHandle(
      MemorySegment openedHandle,
      MemorySegment filename,
      MemorySegment databasePointer,
      int flags,
      MemorySegment vfs) {
    Objects.requireNonNull(filename, "filename");
    Objects.requireNonNull(vfs, "vfs");
    int openFlags = flags;
    if (openFlags == Integer.MIN_VALUE) {
      throw new IllegalStateException("Unsupported open flags.");
    }
    databasePointer.set(ValueLayout.ADDRESS, 0, openedHandle);
    return 0;
  }

  static int failOpenWithDatabaseHandle(
      MemorySegment openedHandle,
      MemorySegment filename,
      MemorySegment databasePointer,
      int flags,
      MemorySegment vfs) {
    Objects.requireNonNull(filename, "filename");
    Objects.requireNonNull(vfs, "vfs");
    int openFlags = flags;
    if (openFlags == Integer.MIN_VALUE) {
      throw new IllegalStateException("Unsupported open flags.");
    }
    databasePointer.set(ValueLayout.ADDRESS, 0, openedHandle);
    return 14;
  }

  static void writeSecureKeyFile(Path keyPath, String keyText) throws IOException {
    if (Files.notExists(keyPath)) {
      SqliteBookKeyFileGenerator.generate(keyPath);
    } else {
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath);
    }
    Files.writeString(keyPath, keyText, StandardCharsets.UTF_8);
  }

  static void withOpenDatabase(BookAccess bookAccess, SqliteDatabaseAction action) {
    try (SqliteNativeDatabase database = SqliteNativeConnections.openKeyFileAccess(bookAccess)) {
      action.run(database);
    }
  }

  /** Performs one checked action against a temporary native SQLite handle. */
  @FunctionalInterface
  interface SqliteDatabaseAction {
    void run(SqliteNativeDatabase database);
  }
}
