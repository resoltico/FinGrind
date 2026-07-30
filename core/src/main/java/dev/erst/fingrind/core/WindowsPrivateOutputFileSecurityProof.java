package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/** Reads and proves the exact protected owner-only DACL shape on one retained Windows handle. */
final class WindowsPrivateOutputFileSecurityProof {
  private WindowsPrivateOutputFileSecurityProof() {}

  static WindowsPrivateOutputFileTransport.SecurityProof read(
      WindowsPrivateOutputFileCalls calls,
      WindowsPrivateOutputFileNative.Handle handle,
      MemorySegment currentOwnerSid)
      throws IOException {
    WindowsPrivateOutputFileCalls checkedCalls = Objects.requireNonNull(calls, "calls");
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment attributes = arena.allocate(8L, Integer.BYTES);
      WindowsPrivateOutputFileNative.requireTrue(
          checkedCalls
              .fileCalls()
              .getFileInformationByHandleEx(
                  Objects.requireNonNull(handle, "handle").segment(),
                  WindowsPrivateOutputFileNative.FILE_ATTRIBUTE_TAG_INFO,
                  attributes,
                  8),
          "GetFileInformationByHandleEx(FileAttributeTagInfo)");
      int flags = attributes.get(ValueLayout.JAVA_INT, 0L);
      return securityDescriptorProof(
          checkedCalls.ownerCalls(),
          checkedCalls.securityCalls(),
          handle,
          Objects.requireNonNull(currentOwnerSid, "currentOwnerSid"),
          (flags & WindowsPrivateOutputFileNative.FILE_ATTRIBUTE_DIRECTORY) == 0
              ? WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE
              : WindowsPrivateOutputFileTransport.EntryKind.DIRECTORY,
          (flags & WindowsPrivateOutputFileNative.FILE_ATTRIBUTE_REPARSE_POINT) != 0,
          arena);
    }
  }

  private static WindowsPrivateOutputFileTransport.SecurityProof securityDescriptorProof(
      WindowsPrivateOutputFileOwnerCalls ownerCalls,
      WindowsPrivateOutputFileSecurityCalls securityCalls,
      WindowsPrivateOutputFileNative.Handle handle,
      MemorySegment currentOwnerSid,
      WindowsPrivateOutputFileTransport.EntryKind entryKind,
      boolean reparsePoint,
      Arena arena)
      throws IOException {
    MemorySegment ownerOut = arena.allocate(ValueLayout.ADDRESS);
    MemorySegment daclOut = arena.allocate(ValueLayout.ADDRESS);
    MemorySegment descriptorOut = arena.allocate(ValueLayout.ADDRESS);
    int result =
        securityCalls.getSecurityInfo(
            handle.segment(),
            WindowsPrivateOutputFileNative.SE_FILE_OBJECT,
            WindowsPrivateOutputFileNative.OWNER_SECURITY_INFORMATION
                | WindowsPrivateOutputFileNative.DACL_SECURITY_INFORMATION,
            ownerOut,
            MemorySegment.NULL,
            daclOut,
            MemorySegment.NULL,
            descriptorOut);
    if (result != 0) {
      throw new IOException(
          "GetSecurityInfo failed with Windows error " + Integer.toUnsignedString(result) + ".");
    }
    MemorySegment descriptor = descriptorOut.get(ValueLayout.ADDRESS, 0L);
    if (descriptor.address() == 0L) {
      throw new IOException("GetSecurityInfo did not return a security descriptor.");
    }
    try {
      MemorySegment actualOwner = ownerOut.get(ValueLayout.ADDRESS, 0L);
      boolean ownerMatches =
          actualOwner.address() != 0L
              && securityCalls.equalSid(actualOwner, currentOwnerSid).value() != 0;
      boolean protectedDacl = protectedDacl(securityCalls, descriptor, arena);
      MemorySegment exactDacl = explicitDacl(securityCalls, descriptor, arena);
      boolean explicit =
          exactDacl.address() != 0L && daclOut.get(ValueLayout.ADDRESS, 0L).address() != 0L;
      AceProof ace =
          explicit
              ? readAceProof(securityCalls, exactDacl, currentOwnerSid, arena)
              : new AceProof(0, false);
      return new WindowsPrivateOutputFileTransport.SecurityProof(
          entryKind,
          reparsePoint,
          ownerMatches,
          protectedDacl,
          explicit,
          ace.count(),
          ace.exactOwnerFullControl());
    } finally {
      WindowsPrivateOutputFileNative.localFree(ownerCalls, descriptor);
    }
  }

  private static boolean protectedDacl(
      WindowsPrivateOutputFileSecurityCalls calls, MemorySegment descriptor, Arena arena)
      throws IOException {
    MemorySegment control = arena.allocate(ValueLayout.JAVA_SHORT);
    MemorySegment revision = arena.allocate(ValueLayout.JAVA_INT);
    WindowsPrivateOutputFileNative.requireTrue(
        calls.getSecurityDescriptorControl(descriptor, control, revision),
        "GetSecurityDescriptorControl");
    return (control.get(ValueLayout.JAVA_SHORT, 0L)
            & WindowsPrivateOutputFileNative.SE_DACL_PROTECTED)
        != 0;
  }

  private static MemorySegment explicitDacl(
      WindowsPrivateOutputFileSecurityCalls calls, MemorySegment descriptor, Arena arena)
      throws IOException {
    MemorySegment present = arena.allocate(ValueLayout.JAVA_INT);
    MemorySegment dacl = arena.allocate(ValueLayout.ADDRESS);
    MemorySegment defaulted = arena.allocate(ValueLayout.JAVA_INT);
    WindowsPrivateOutputFileNative.requireTrue(
        calls.getSecurityDescriptorDacl(descriptor, present, dacl, defaulted),
        "GetSecurityDescriptorDacl");
    if (present.get(ValueLayout.JAVA_INT, 0L) == 0) {
      return MemorySegment.NULL;
    }
    return dacl.get(ValueLayout.ADDRESS, 0L);
  }

  private static AceProof readAceProof(
      WindowsPrivateOutputFileSecurityCalls calls,
      MemorySegment dacl,
      MemorySegment currentOwnerSid,
      Arena arena)
      throws IOException {
    MemorySegment info = arena.allocate(12L, Integer.BYTES);
    WindowsPrivateOutputFileNative.requireTrue(
        calls.getAclInformation(
            dacl, info, 12, WindowsPrivateOutputFileNative.ACL_SIZE_INFORMATION),
        "GetAclInformation");
    int count = info.get(ValueLayout.JAVA_INT, 0L);
    if (count != 1) {
      return new AceProof(count, false);
    }
    MemorySegment aceOut = arena.allocate(ValueLayout.ADDRESS);
    WindowsPrivateOutputFileNative.requireTrue(calls.getAce(dacl, 0, aceOut), "GetAce");
    MemorySegment ace = aceOut.get(ValueLayout.ADDRESS, 0L);
    if (ace.address() == 0L) {
      return new AceProof(count, false);
    }
    MemorySegment header = ace.reinterpret(8L);
    int sidLength = calls.getLengthSid(currentOwnerSid).value();
    boolean exact =
        sidLength > 0
            && Byte.toUnsignedInt(header.get(ValueLayout.JAVA_BYTE, 0L))
                == WindowsPrivateOutputFileNative.ACCESS_ALLOWED_ACE_TYPE
            && Byte.toUnsignedInt(header.get(ValueLayout.JAVA_BYTE, 1L)) == 0
            && Short.toUnsignedInt(header.get(ValueLayout.JAVA_SHORT, 2L)) == 8 + sidLength
            && header.get(ValueLayout.JAVA_INT, 4L)
                == WindowsPrivateOutputFileNative.FILE_ALL_ACCESS
            && calls.equalSid(MemorySegment.ofAddress(ace.address() + 8L), currentOwnerSid).value()
                != 0;
    return new AceProof(count, exact);
  }

  private record AceProof(int count, boolean exactOwnerFullControl) {}
}
