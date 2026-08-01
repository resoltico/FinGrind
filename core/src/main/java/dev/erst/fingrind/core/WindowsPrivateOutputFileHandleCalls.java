package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

/** Win32 calls that operate on a retained protected file or directory handle. */
interface WindowsPrivateOutputFileHandleCalls {
  /** Invokes Win32 {@code CreateDirectoryW}. */
  WindowsPrivateOutputFileNative.Result<Integer> createDirectoryW(
      MemorySegment directory, MemorySegment securityAttributes) throws IOException;

  /** Invokes Win32 {@code CreateFileW}. */
  WindowsPrivateOutputFileNative.Result<Long> createFileW(
      MemorySegment fileName,
      int desiredAccess,
      int shareMode,
      MemorySegment securityAttributes,
      int creationDisposition,
      int flagsAndAttributes,
      MemorySegment templateFile)
      throws IOException;

  /** Invokes Win32 {@code ReadFile}. */
  WindowsPrivateOutputFileNative.Result<Integer> readFile(
      MemorySegment handle,
      MemorySegment bytes,
      int byteCount,
      MemorySegment transferred,
      MemorySegment overlapped)
      throws IOException;

  /** Invokes Win32 {@code WriteFile}. */
  WindowsPrivateOutputFileNative.Result<Integer> writeFile(
      MemorySegment handle,
      MemorySegment bytes,
      int byteCount,
      MemorySegment transferred,
      MemorySegment overlapped)
      throws IOException;

  /** Invokes Win32 {@code FlushFileBuffers}. */
  WindowsPrivateOutputFileNative.Result<Integer> flushFileBuffers(MemorySegment handle)
      throws IOException;

  /** Invokes Win32 {@code CloseHandle}. */
  WindowsPrivateOutputFileNative.Result<Integer> closeHandle(MemorySegment handle)
      throws IOException;

  /** Invokes Win32 {@code GetFileInformationByHandleEx}. */
  WindowsPrivateOutputFileNative.Result<Integer> getFileInformationByHandleEx(
      MemorySegment handle, int informationClass, MemorySegment information, int byteCount)
      throws IOException;

  /** Invokes Win32 {@code GetFileSizeEx}. */
  WindowsPrivateOutputFileNative.Result<Integer> getFileSizeEx(
      MemorySegment handle, MemorySegment size) throws IOException;

  /** Invokes Win32 {@code SetFilePointerEx}. */
  WindowsPrivateOutputFileNative.Result<Integer> setFilePointerEx(
      MemorySegment handle, long position, MemorySegment newPosition, int moveMethod)
      throws IOException;

  /** Invokes Win32 {@code SetEndOfFile}. */
  WindowsPrivateOutputFileNative.Result<Integer> setEndOfFile(MemorySegment handle)
      throws IOException;

  /** Invokes Win32 {@code LockFileEx}. */
  WindowsPrivateOutputFileNative.Result<Integer> lockFileEx(
      MemorySegment handle,
      int flags,
      int reserved,
      int byteCountLow,
      int byteCountHigh,
      MemorySegment overlapped)
      throws IOException;

  /** Invokes Win32 {@code UnlockFileEx}. */
  WindowsPrivateOutputFileNative.Result<Integer> unlockFileEx(
      MemorySegment handle,
      int reserved,
      int byteCountLow,
      int byteCountHigh,
      MemorySegment overlapped)
      throws IOException;
}
