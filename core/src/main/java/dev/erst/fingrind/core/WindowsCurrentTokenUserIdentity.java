package dev.erst.fingrind.core;

import java.io.IOException;
import java.util.Objects;

/** Stable current-token identity with an account name resolved directly from its native SID. */
record WindowsCurrentTokenUserIdentity(String sidText, String accountName) {
  WindowsCurrentTokenUserIdentity {
    Objects.requireNonNull(sidText, "sidText");
    Objects.requireNonNull(accountName, "accountName");
  }

  static WindowsCurrentTokenUserIdentity resolve(WindowsPrivateOutputFileCalls calls)
      throws IOException {
    try (WindowsPrivateOutputFileOwner owner = WindowsPrivateOutputFileOwner.acquire(calls)) {
      return new WindowsCurrentTokenUserIdentity(
          owner.ownerSidText(),
          WindowsPrivateOutputFileAccountNameResolver.resolve(
              owner.ownerCalls(), owner.ownerSid()));
    }
  }
}
