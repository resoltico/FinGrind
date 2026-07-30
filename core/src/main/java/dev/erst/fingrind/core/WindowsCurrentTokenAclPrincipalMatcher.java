package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.attribute.UserPrincipal;
import java.util.Objects;
import java.util.Optional;

/** Matches observed ACL principals to one retained current Windows token by native SID. */
final class WindowsCurrentTokenAclPrincipalMatcher
    implements WindowsTrustedAclPrincipalResolver.CurrentTokenUserPrincipalMatcher {
  private final WindowsPrivateOutputFileOwner owner;
  private final WindowsPrivateOutputFileSecurityCalls securityCalls;
  private boolean closed;

  private WindowsCurrentTokenAclPrincipalMatcher(
      WindowsPrivateOutputFileOwner owner, WindowsPrivateOutputFileSecurityCalls securityCalls) {
    this.owner = Objects.requireNonNull(owner, "owner");
    this.securityCalls = Objects.requireNonNull(securityCalls, "securityCalls");
  }

  /**
   * Acquires one current-token capability for a complete ACL admission decision.
   *
   * <p>Its caller must release the matcher after all observed ACL principals have been considered.
   */
  static WindowsCurrentTokenAclPrincipalMatcher acquire(WindowsPrivateOutputFileCalls calls)
      throws IOException {
    WindowsPrivateOutputFileCalls checkedCalls = Objects.requireNonNull(calls, "calls");
    return new WindowsCurrentTokenAclPrincipalMatcher(
        WindowsPrivateOutputFileOwner.acquire(checkedCalls), checkedCalls.securityCalls());
  }

  /**
   * Returns whether the observed principal resolves to exactly the current process token SID.
   *
   * <p>Account display names never authorize the match: Win32 resolves the observed principal to a
   * binary SID and {@code EqualSid} compares it with the retained current-token SID.
   */
  @Override
  public boolean matchesCurrentToken(UserPrincipal principal) throws IOException {
    requireOpen();
    UserPrincipal checkedPrincipal = Objects.requireNonNull(principal, "principal");
    String accountName = Objects.requireNonNull(checkedPrincipal.getName(), "ACL principal name");
    if (accountName.isBlank()) {
      return false;
    }
    if (WindowsPrivateOutputFileSid.isText(accountName)) {
      return owner.ownerSidText().equalsIgnoreCase(accountName);
    }
    try (Arena arena = Arena.ofConfined()) {
      Optional<MemorySegment> candidateSid =
          WindowsPrivateOutputFileAccountSidResolver.resolve(
              owner.ownerCalls(), arena, accountName);
      return candidateSid.isPresent()
          && securityCalls.equalSid(owner.ownerSid(), candidateSid.orElseThrow()).value() != 0;
    }
  }

  @Override
  public void release() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    owner.close();
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("The Windows current-token ACL matcher is already closed.");
    }
  }
}
