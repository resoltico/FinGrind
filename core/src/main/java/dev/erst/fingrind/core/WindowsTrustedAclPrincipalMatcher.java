package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;

/** Recognizes only Windows' fixed operating-system ACL trust-boundary SIDs. */
final class WindowsTrustedAclPrincipalMatcher {
  private static final List<String> TRUSTED_OPERATING_SYSTEM_SID_TEXT =
      List.of(
          "S-1-5-18",
          "S-1-5-32-544",
          "S-1-5-80-956008885-3418522649-1831038044-1853292631-2271478464");

  private WindowsTrustedAclPrincipalMatcher() {}

  /** Returns whether the observed principal resolves to a fixed Windows operating-system owner. */
  static boolean matchesTrusted(
      WindowsPrivateOutputFileCalls calls, UserPrincipal observedPrincipal) throws IOException {
    WindowsPrivateOutputFileCalls checkedCalls = Objects.requireNonNull(calls, "calls");
    UserPrincipal checkedPrincipal = Objects.requireNonNull(observedPrincipal, "observedPrincipal");
    return WindowsPrivateOutputFileSid.resolveText(checkedCalls.ownerCalls(), checkedPrincipal)
        .map(WindowsTrustedAclPrincipalMatcher::isTrustedSidText)
        .orElse(false);
  }

  private static boolean isTrustedSidText(String candidateSidText) {
    return TRUSTED_OPERATING_SYSTEM_SID_TEXT.stream().anyMatch(candidateSidText::equalsIgnoreCase);
  }
}
