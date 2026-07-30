package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

/** Win32 calls that inspect a retained handle's owner and protected DACL evidence. */
interface WindowsPrivateOutputFileSecurityCalls {
  /** Invokes Win32 {@code GetSecurityInfo}. */
  int getSecurityInfo(
      MemorySegment handle,
      int objectType,
      int securityInformation,
      MemorySegment owner,
      MemorySegment group,
      MemorySegment dacl,
      MemorySegment sacl,
      MemorySegment descriptor)
      throws IOException;

  /** Invokes Win32 {@code GetSecurityDescriptorControl}. */
  WindowsPrivateOutputFileNative.Result<Integer> getSecurityDescriptorControl(
      MemorySegment descriptor, MemorySegment control, MemorySegment revision) throws IOException;

  /** Invokes Win32 {@code GetSecurityDescriptorDacl}. */
  WindowsPrivateOutputFileNative.Result<Integer> getSecurityDescriptorDacl(
      MemorySegment descriptor,
      MemorySegment daclPresent,
      MemorySegment dacl,
      MemorySegment defaulted)
      throws IOException;

  /** Invokes Win32 {@code GetAclInformation}. */
  WindowsPrivateOutputFileNative.Result<Integer> getAclInformation(
      MemorySegment dacl, MemorySegment information, int informationLength, int informationClass)
      throws IOException;

  /** Invokes Win32 {@code GetAce}. */
  WindowsPrivateOutputFileNative.Result<Integer> getAce(
      MemorySegment dacl, int index, MemorySegment ace) throws IOException;

  /** Invokes Win32 {@code GetLengthSid}. */
  WindowsPrivateOutputFileNative.Result<Integer> getLengthSid(MemorySegment sid) throws IOException;

  /** Invokes Win32 {@code EqualSid}. */
  WindowsPrivateOutputFileNative.Result<Integer> equalSid(
      MemorySegment firstSid, MemorySegment secondSid) throws IOException;
}
