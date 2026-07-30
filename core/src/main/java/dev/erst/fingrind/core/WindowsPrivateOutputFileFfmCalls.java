package dev.erst.fingrind.core;

import java.util.Objects;

/** Creates the FFM adapters for each protected-output Win32 call family. */
final class WindowsPrivateOutputFileFfmCalls {
  private WindowsPrivateOutputFileFfmCalls() {}

  /** Creates deterministic or native FFM call adapters over one complete binding vocabulary. */
  static WindowsPrivateOutputFileCalls fromBindings(WindowsPrivateOutputFileBindings bindings) {
    WindowsPrivateOutputFileBindings checkedBindings = Objects.requireNonNull(bindings, "bindings");
    return new WindowsPrivateOutputFileCalls(
        new WindowsPrivateOutputFileHandleFfmCalls(checkedBindings.fileBindings()),
        new WindowsPrivateOutputFileOwnerFfmCalls(checkedBindings.ownerBindings()),
        new WindowsPrivateOutputFileSecurityFfmCalls(checkedBindings.securityBindings()));
  }
}
