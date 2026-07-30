package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/** FFM adapter for retained file and directory handle operations. */
final class WindowsPrivateOutputFileHandleFfmCalls implements WindowsPrivateOutputFileHandleCalls {
  private final WindowsPrivateOutputFileHandleBindings bindings;

  WindowsPrivateOutputFileHandleFfmCalls(WindowsPrivateOutputFileHandleBindings bindings) {
    this.bindings = Objects.requireNonNull(bindings, "bindings");
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> createDirectoryW(
      MemorySegment directory, MemorySegment securityAttributes) throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.createDirectoryW, directory, securityAttributes);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Long> createFileW(
      MemorySegment fileName,
      int desiredAccess,
      int shareMode,
      MemorySegment securityAttributes,
      int creationDisposition,
      int flagsAndAttributes,
      MemorySegment templateFile)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeAddress(
        bindings.createFileW,
        fileName,
        desiredAccess,
        shareMode,
        securityAttributes,
        creationDisposition,
        flagsAndAttributes,
        templateFile);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> readFile(
      MemorySegment handle,
      MemorySegment bytes,
      int byteCount,
      MemorySegment transferred,
      MemorySegment overlapped)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.readFile, handle, bytes, byteCount, transferred, overlapped);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> writeFile(
      MemorySegment handle,
      MemorySegment bytes,
      int byteCount,
      MemorySegment transferred,
      MemorySegment overlapped)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.writeFile, handle, bytes, byteCount, transferred, overlapped);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> flushFileBuffers(MemorySegment handle)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(bindings.flushFileBuffers, handle);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> closeHandle(MemorySegment handle)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(bindings.closeHandle, handle);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> getFileInformationByHandleEx(
      MemorySegment handle, int informationClass, MemorySegment information, int byteCount)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.getFileInformationByHandleEx, handle, informationClass, information, byteCount);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> getFileSizeEx(
      MemorySegment handle, MemorySegment size) throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(bindings.getFileSizeEx, handle, size);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> setFilePointerEx(
      MemorySegment handle, long position, MemorySegment newPosition, int moveMethod)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.setFilePointerEx, handle, position, newPosition, moveMethod);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> setEndOfFile(MemorySegment handle)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(bindings.setEndOfFile, handle);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> lockFileEx(
      MemorySegment handle,
      int flags,
      int reserved,
      int byteCountLow,
      int byteCountHigh,
      MemorySegment overlapped)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.lockFileEx, handle, flags, reserved, byteCountLow, byteCountHigh, overlapped);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> unlockFileEx(
      MemorySegment handle,
      int reserved,
      int byteCountLow,
      int byteCountHigh,
      MemorySegment overlapped)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.unlockFileEx, handle, reserved, byteCountLow, byteCountHigh, overlapped);
  }
}
