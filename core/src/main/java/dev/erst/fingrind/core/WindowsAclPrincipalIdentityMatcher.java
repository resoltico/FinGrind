package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.attribute.UserPrincipal;
import java.util.Objects;
import java.util.Optional;

/** Compares observed Windows ACL principals through their canonical native security identifiers. */
final class WindowsAclPrincipalIdentityMatcher {
  private WindowsAclPrincipalIdentityMatcher() {}

  /** Returns whether both observed principals resolve to the same native SID. */
  static boolean matches(
      WindowsPrivateOutputFileCalls calls,
      UserPrincipal firstPrincipal,
      UserPrincipal secondPrincipal)
      throws IOException {
    WindowsPrivateOutputFileCalls checkedCalls = Objects.requireNonNull(calls, "calls");
    UserPrincipal checkedFirstPrincipal = Objects.requireNonNull(firstPrincipal, "firstPrincipal");
    UserPrincipal checkedSecondPrincipal =
        Objects.requireNonNull(secondPrincipal, "secondPrincipal");
    Optional<String> firstSid =
        WindowsPrivateOutputFileSid.resolveText(checkedCalls.ownerCalls(), checkedFirstPrincipal);
    if (firstSid.isEmpty()) {
      return false;
    }
    Optional<String> secondSid =
        WindowsPrivateOutputFileSid.resolveText(checkedCalls.ownerCalls(), checkedSecondPrincipal);
    return secondSid.isPresent()
        && firstSid.orElseThrow().equalsIgnoreCase(secondSid.orElseThrow());
  }
}
