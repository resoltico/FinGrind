package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

/** Win32 calls that establish and materialize the current owner's protected descriptor. */
interface WindowsPrivateOutputFileOwnerCalls {
  /** Invokes Win32 {@code GetCurrentProcess}. */
  WindowsPrivateOutputFileNative.Result<Long> getCurrentProcess() throws IOException;

  /** Invokes Win32 {@code OpenProcessToken}. */
  WindowsPrivateOutputFileNative.Result<Integer> openProcessToken(
      MemorySegment process, int desiredAccess, MemorySegment token) throws IOException;

  /** Invokes Win32 {@code GetTokenInformation}. */
  WindowsPrivateOutputFileNative.Result<Integer> getTokenInformation(
      MemorySegment token,
      int informationClass,
      MemorySegment information,
      int informationLength,
      MemorySegment returnedLength)
      throws IOException;

  /** Invokes Win32 {@code LocalFree}. */
  WindowsPrivateOutputFileNative.Result<Long> localFree(MemorySegment allocation)
      throws IOException;

  /** Invokes Win32 {@code ConvertSidToStringSidW}. */
  WindowsPrivateOutputFileNative.Result<Integer> convertSidToStringSidW(
      MemorySegment sid, MemorySegment sidText) throws IOException;

  /** Invokes Win32 {@code LookupAccountSidW}. */
  WindowsPrivateOutputFileNative.Result<Integer> lookupAccountSidW(
      MemorySegment systemName,
      MemorySegment sid,
      MemorySegment referencedDomainName,
      MemorySegment referencedDomainNameCharacters,
      MemorySegment accountName,
      MemorySegment accountNameCharacters,
      MemorySegment sidNameUse)
      throws IOException;

  /** Invokes Win32 {@code ConvertStringSecurityDescriptorToSecurityDescriptorW}. */
  WindowsPrivateOutputFileNative.Result<Integer>
      convertStringSecurityDescriptorToSecurityDescriptorW(
          MemorySegment descriptorText,
          int revision,
          MemorySegment descriptor,
          MemorySegment descriptorLength)
          throws IOException;
}
