package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Recognizes only Windows' fixed operating-system ACL trust-boundary SIDs. */
final class WindowsTrustedAclPrincipalMatcher {
  private static final List<String> TRUSTED_SID_TEXT = List.of("S-1-5-18", "S-1-5-32-544");

  private WindowsTrustedAclPrincipalMatcher() {}

  /** Returns whether the observed principal resolves to LocalSystem or built-in Administrators. */
  static boolean matchesTrusted(
      WindowsPrivateOutputFileCalls calls, UserPrincipal observedPrincipal) throws IOException {
    WindowsPrivateOutputFileCalls checkedCalls = Objects.requireNonNull(calls, "calls");
    UserPrincipal checkedPrincipal = Objects.requireNonNull(observedPrincipal, "observedPrincipal");
    String accountName = Objects.requireNonNull(checkedPrincipal.getName(), "ACL principal name");
    if (accountName.isBlank()) {
      return false;
    }
    if (WindowsPrivateOutputFileSid.isText(accountName)) {
      return isTrustedSidText(accountName);
    }
    try (Arena arena = Arena.ofConfined()) {
      Optional<MemorySegment> candidateSid =
          WindowsPrivateOutputFileAccountSidResolver.resolve(
              checkedCalls.ownerCalls(), arena, accountName);
      return candidateSid.isPresent()
          && isTrustedSidText(
              WindowsPrivateOutputFileSid.toText(
                  checkedCalls.ownerCalls(), candidateSid.orElseThrow()));
    }
  }

  private static boolean isTrustedSidText(String candidateSidText) {
    return TRUSTED_SID_TEXT.stream().anyMatch(candidateSidText::equalsIgnoreCase);
  }
}
