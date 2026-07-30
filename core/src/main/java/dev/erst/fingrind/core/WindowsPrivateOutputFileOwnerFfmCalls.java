package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/** FFM adapter for current-owner and protected-descriptor construction. */
final class WindowsPrivateOutputFileOwnerFfmCalls implements WindowsPrivateOutputFileOwnerCalls {
  private final WindowsPrivateOutputFileOwnerBindings bindings;

  WindowsPrivateOutputFileOwnerFfmCalls(WindowsPrivateOutputFileOwnerBindings bindings) {
    this.bindings = Objects.requireNonNull(bindings, "bindings");
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Long> getCurrentProcess() throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeAddress(bindings.getCurrentProcess);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> openProcessToken(
      MemorySegment process, int desiredAccess, MemorySegment token) throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.openProcessToken, process, desiredAccess, token);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> getTokenInformation(
      MemorySegment token,
      int informationClass,
      MemorySegment information,
      int informationLength,
      MemorySegment returnedLength)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.getTokenInformation,
        token,
        informationClass,
        information,
        informationLength,
        returnedLength);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Long> localFree(MemorySegment allocation)
      throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeAddress(bindings.localFree, allocation);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer> convertSidToStringSidW(
      MemorySegment sid, MemorySegment sidText) throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.convertSidToStringSidW, sid, sidText);
  }

  @Override
  public WindowsPrivateOutputFileNative.Result<Integer>
      convertStringSecurityDescriptorToSecurityDescriptorW(
          MemorySegment descriptorText,
          int revision,
          MemorySegment descriptor,
          MemorySegment descriptorLength)
          throws IOException {
    return WindowsPrivateOutputFileFfmInvocation.invokeInt(
        bindings.convertStringSecurityDescriptorToSecurityDescriptorW,
        descriptorText,
        revision,
        descriptor,
        descriptorLength);
  }
}
