package dev.erst.fingrind.core;

import java.lang.foreign.MemorySegment;

/** Explicit no-reflection defaults for narrow Win32 call-family test doubles. */
final class WindowsPrivateOutputFileCallTestSupport {
  private WindowsPrivateOutputFileCallTestSupport() {}

  /** Fails every unconfigured retained-handle native operation. */
  static class HandleCalls implements WindowsPrivateOutputFileHandleCalls {
    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> createDirectoryW(
        MemorySegment directory, MemorySegment securityAttributes) {
      throw unexpected("CreateDirectoryW");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Long> createFileW(
        MemorySegment fileName,
        int desiredAccess,
        int shareMode,
        MemorySegment securityAttributes,
        int creationDisposition,
        int flagsAndAttributes,
        MemorySegment templateFile) {
      throw unexpected("CreateFileW");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> readFile(
        MemorySegment handle,
        MemorySegment bytes,
        int byteCount,
        MemorySegment transferred,
        MemorySegment overlapped) {
      throw unexpected("ReadFile");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> writeFile(
        MemorySegment handle,
        MemorySegment bytes,
        int byteCount,
        MemorySegment transferred,
        MemorySegment overlapped) {
      throw unexpected("WriteFile");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> flushFileBuffers(MemorySegment handle) {
      throw unexpected("FlushFileBuffers");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> closeHandle(MemorySegment handle) {
      throw unexpected("CloseHandle");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> getFileInformationByHandleEx(
        MemorySegment handle, int informationClass, MemorySegment information, int byteCount) {
      throw unexpected("GetFileInformationByHandleEx");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> getFileSizeEx(
        MemorySegment handle, MemorySegment size) {
      throw unexpected("GetFileSizeEx");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> setFilePointerEx(
        MemorySegment handle, long position, MemorySegment newPosition, int moveMethod) {
      throw unexpected("SetFilePointerEx");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> setEndOfFile(MemorySegment handle) {
      throw unexpected("SetEndOfFile");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> lockFileEx(
        MemorySegment handle,
        int flags,
        int reserved,
        int byteCountLow,
        int byteCountHigh,
        MemorySegment overlapped) {
      throw unexpected("LockFileEx");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> unlockFileEx(
        MemorySegment handle,
        int reserved,
        int byteCountLow,
        int byteCountHigh,
        MemorySegment overlapped) {
      throw unexpected("UnlockFileEx");
    }
  }

  /** Fails every unconfigured current-token-user native operation. */
  static class OwnerCalls implements WindowsPrivateOutputFileOwnerCalls {
    @Override
    public WindowsPrivateOutputFileNative.Result<Long> getCurrentProcess() {
      throw unexpected("GetCurrentProcess");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> openProcessToken(
        MemorySegment process, int desiredAccess, MemorySegment token) {
      throw unexpected("OpenProcessToken");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> getTokenInformation(
        MemorySegment token,
        int informationClass,
        MemorySegment information,
        int informationLength,
        MemorySegment returnedLength) {
      throw unexpected("GetTokenInformation");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Long> localFree(MemorySegment allocation) {
      throw unexpected("LocalFree");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> convertSidToStringSidW(
        MemorySegment sid, MemorySegment sidText) {
      throw unexpected("ConvertSidToStringSidW");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> lookupAccountNameW(
        MemorySegment systemName,
        MemorySegment accountName,
        MemorySegment sid,
        MemorySegment sidBytes,
        MemorySegment referencedDomainName,
        MemorySegment referencedDomainNameCharacters,
        MemorySegment sidNameUse) {
      throw unexpected("LookupAccountNameW");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer>
        convertStringSecurityDescriptorToSecurityDescriptorW(
            MemorySegment descriptorText,
            int revision,
            MemorySegment descriptor,
            MemorySegment descriptorLength) {
      throw unexpected("ConvertStringSecurityDescriptorToSecurityDescriptorW");
    }
  }

  /** Fails every unconfigured security-proof native operation. */
  static class SecurityCalls implements WindowsPrivateOutputFileSecurityCalls {
    @Override
    public int getSecurityInfo(
        MemorySegment handle,
        int objectType,
        int securityInformation,
        MemorySegment owner,
        MemorySegment group,
        MemorySegment dacl,
        MemorySegment sacl,
        MemorySegment descriptor) {
      throw unexpected("GetSecurityInfo");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> getSecurityDescriptorControl(
        MemorySegment descriptor, MemorySegment control, MemorySegment revision) {
      throw unexpected("GetSecurityDescriptorControl");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> getSecurityDescriptorDacl(
        MemorySegment descriptor,
        MemorySegment daclPresent,
        MemorySegment dacl,
        MemorySegment defaulted) {
      throw unexpected("GetSecurityDescriptorDacl");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> getAclInformation(
        MemorySegment dacl,
        MemorySegment information,
        int informationLength,
        int informationClass) {
      throw unexpected("GetAclInformation");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> getAce(
        MemorySegment dacl, int index, MemorySegment ace) {
      throw unexpected("GetAce");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> getLengthSid(MemorySegment sid) {
      throw unexpected("GetLengthSid");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> equalSid(
        MemorySegment firstSid, MemorySegment secondSid) {
      throw unexpected("EqualSid");
    }
  }

  static AssertionError unexpected(String operation) {
    return new AssertionError("Unexpected Win32 test-double call: " + operation);
  }
}
