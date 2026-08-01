package dev.erst.fingrind.core;

import java.util.Objects;

/** Groups the narrow Win32 call families required by protected-output operations. */
record WindowsPrivateOutputFileCalls(
    WindowsPrivateOutputFileHandleCalls fileCalls,
    WindowsPrivateOutputFileOwnerCalls ownerCalls,
    WindowsPrivateOutputFileSecurityCalls securityCalls) {
  WindowsPrivateOutputFileCalls {
    Objects.requireNonNull(fileCalls, "fileCalls");
    Objects.requireNonNull(ownerCalls, "ownerCalls");
    Objects.requireNonNull(securityCalls, "securityCalls");
  }
}
