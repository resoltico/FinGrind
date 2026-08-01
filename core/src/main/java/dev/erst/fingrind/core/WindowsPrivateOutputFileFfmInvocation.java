package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** Invokes one linked Win32 method handle with the correct captured-last-error convention. */
final class WindowsPrivateOutputFileFfmInvocation {
  private static final java.lang.foreign.MemoryLayout CAPTURE_STATE_LAYOUT =
      Linker.Option.captureStateLayout();

  private WindowsPrivateOutputFileFfmInvocation() {}

  static WindowsPrivateOutputFileNative.Result<Integer> invokeInt(
      MethodHandle handle, Object... arguments) throws IOException {
    WindowsPrivateOutputFileNative.Result<Object> result = invokeCaptured(handle, arguments);
    return new WindowsPrivateOutputFileNative.Result<>((int) result.value(), result.lastError());
  }

  static WindowsPrivateOutputFileNative.Result<Long> invokeAddress(
      MethodHandle handle, Object... arguments) throws IOException {
    WindowsPrivateOutputFileNative.Result<Object> result = invokeCaptured(handle, arguments);
    return new WindowsPrivateOutputFileNative.Result<>(
        ((MemorySegment) result.value()).address(), result.lastError());
  }

  static int invokeDirectInt(MethodHandle handle, Object... arguments) throws IOException {
    try {
      return (int) handle.invokeWithArguments(arguments);
    } catch (RuntimeException | Error exception) {
      throw exception;
    } catch (Throwable exception) {
      throw new IOException(
          "FinGrind could not invoke one Windows private-output operation.", exception);
    }
  }

  private static WindowsPrivateOutputFileNative.Result<Object> invokeCaptured(
      MethodHandle handle, Object... arguments) throws IOException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment state = arena.allocate(CAPTURE_STATE_LAYOUT);
      Object[] captured = new Object[arguments.length + 1];
      captured[0] = state;
      System.arraycopy(arguments, 0, captured, 1, arguments.length);
      Object value = handle.invokeWithArguments(captured);
      return new WindowsPrivateOutputFileNative.Result<>(
          value, state.get(ValueLayout.JAVA_INT, 0L));
    } catch (RuntimeException | Error exception) {
      throw exception;
    } catch (Throwable exception) {
      throw new IOException(
          "FinGrind could not invoke one Windows private-output operation.", exception);
    }
  }
}
