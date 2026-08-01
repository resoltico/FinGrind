package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Method-handle and native-close/open fixture helpers for SQLite bridge tests. */
final class SqliteNativeHandleFixtures {
  private SqliteNativeHandleFixtures() {}

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

  static int recordSuccessfulShutdownCall(AtomicInteger shutdownCalls) {
    shutdownCalls.incrementAndGet();
    return SqliteNativeResultCode.code("OK");
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
    if (flags == Integer.MIN_VALUE) {
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
    if (flags == Integer.MIN_VALUE) {
      throw new IllegalStateException("Unsupported open flags.");
    }
    databasePointer.set(ValueLayout.ADDRESS, 0, openedHandle);
    return 14;
  }
}
