package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** Raw Win32 bindings for file and directory handle lifecycle operations. */
final class WindowsPrivateOutputFileHandleBindings {
  final MethodHandle createDirectoryW;
  final MethodHandle createFileW;
  final MethodHandle readFile;
  final MethodHandle writeFile;
  final MethodHandle flushFileBuffers;
  final MethodHandle closeHandle;
  final MethodHandle getFileInformationByHandleEx;
  final MethodHandle getFileSizeEx;
  final MethodHandle setFilePointerEx;
  final MethodHandle setEndOfFile;
  final MethodHandle lockFileEx;
  final MethodHandle unlockFileEx;

  WindowsPrivateOutputFileHandleBindings(MethodHandle... calls) {
    Objects.requireNonNull(calls, "calls");
    if (calls.length != 12) {
      throw new IllegalArgumentException(
          "The Windows protected-output handle table is incomplete.");
    }
    for (int index = 0; index < calls.length; index++) {
      Objects.requireNonNull(calls[index], "Windows protected-output handle binding " + index);
    }
    createDirectoryW = calls[0];
    createFileW = calls[1];
    readFile = calls[2];
    writeFile = calls[3];
    flushFileBuffers = calls[4];
    closeHandle = calls[5];
    getFileInformationByHandleEx = calls[6];
    getFileSizeEx = calls[7];
    setFilePointerEx = calls[8];
    setEndOfFile = calls[9];
    lockFileEx = calls[10];
    unlockFileEx = calls[11];
  }

  static WindowsPrivateOutputFileHandleBindings bind(
      SymbolLookup kernel32, WindowsPrivateOutputFileBindingSupport.Binder binder)
      throws IOException {
    return new WindowsPrivateOutputFileHandleBindings(
        binder.captured(
            kernel32,
            "CreateDirectoryW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
        binder.captured(
            kernel32,
            "CreateFileW",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS)),
        binder.captured(
            kernel32,
            "ReadFile",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS)),
        binder.captured(
            kernel32,
            "WriteFile",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS)),
        binder.captured(
            kernel32,
            "FlushFileBuffers",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
        binder.captured(
            kernel32,
            "CloseHandle",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
        binder.captured(
            kernel32,
            "GetFileInformationByHandleEx",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT)),
        binder.captured(
            kernel32,
            "GetFileSizeEx",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
        binder.captured(
            kernel32,
            "SetFilePointerEx",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT)),
        binder.captured(
            kernel32,
            "SetEndOfFile",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
        binder.captured(
            kernel32,
            "LockFileEx",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS)),
        binder.captured(
            kernel32,
            "UnlockFileEx",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS)));
  }
}
