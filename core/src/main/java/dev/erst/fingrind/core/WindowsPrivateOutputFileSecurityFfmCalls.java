package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/** FFM adapter for retained-handle owner and DACL proof. */
final class WindowsPrivateOutputFileSecurityFfmCalls
    implements WindowsPrivateOutputFileSecurityCalls {
  private final WindowsPrivateOutputFileSecurityBindings bindings;

  WindowsPrivateOutputFileSecurityFfmCalls(WindowsPrivateOutputFileSecurityBindings bindings) {
    this.bindings = Objects.requireNonNull(bindings, "bindings");
  }

  @Override
  public int getSecurityInfo(
      MemorySegment handle,
      int objectType,
      int securityInformation,
      MemorySegment owner,
      MemorySegment group,
      MemorySegment dacl,
      MemorySegment sacl,
      MemorySegment descriptor)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeDirectInt(
        bindings.getSecurityInfo,
        handle,
        objectType,
        securityInformation,
        owner,
        group,
        dacl,
        sacl,
        descriptor);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> getSecurityDescriptorControl(
      MemorySegment descriptor, MemorySegment control, MemorySegment revision) throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.getSecurityDescriptorControl, descriptor, control, revision);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> getSecurityDescriptorDacl(
      MemorySegment descriptor,
      MemorySegment daclPresent,
      MemorySegment dacl,
      MemorySegment defaulted)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.getSecurityDescriptorDacl, descriptor, daclPresent, dacl, defaulted);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> getAclInformation(
      MemorySegment dacl, MemorySegment information, int informationLength, int informationClass)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.getAclInformation, dacl, information, informationLength, informationClass);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> getAce(
      MemorySegment dacl, int index, MemorySegment ace) throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(bindings.getAce, dacl, index, ace);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> getLengthSid(MemorySegment sid)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(bindings.getLengthSid, sid);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> equalSid(
      MemorySegment firstSid, MemorySegment secondSid) throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(bindings.equalSid, firstSid, secondSid);
  }
}
