package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Resolves the Windows privileged principals that form the local filesystem trust boundary. */
final class WindowsTrustedAclPrincipalResolver {
  private static final List<KnownUntrustedPrincipal> KNOWN_UNTRUSTED_PRINCIPALS =
      List.of(
          new KnownUntrustedPrincipal(
              PrivateOutputDirectory.AclMutationPrincipalKind.CREATOR_OWNER,
              List.of("S-1-3-0", "CREATOR OWNER")),
          new KnownUntrustedPrincipal(
              PrivateOutputDirectory.AclMutationPrincipalKind.AUTHENTICATED_USERS,
              List.of("S-1-5-11", "NT AUTHORITY\\Authenticated Users")),
          new KnownUntrustedPrincipal(
              PrivateOutputDirectory.AclMutationPrincipalKind.BUILTIN_USERS,
              List.of("S-1-5-32-545", "BUILTIN\\Users")),
          new KnownUntrustedPrincipal(
              PrivateOutputDirectory.AclMutationPrincipalKind.EVERYONE,
              List.of("S-1-1-0", "Everyone")));

  private WindowsTrustedAclPrincipalResolver() {}

  static boolean isTrustedForCurrentPlatform(
      UserPrincipal candidate, TrustedAclPrincipalMatcherSource matcherSource) throws IOException {
    return isTrustedForOperatingSystem(candidate, System.getProperty("os.name", ""), matcherSource);
  }

  static boolean isTrustedForOperatingSystem(
      UserPrincipal candidate,
      String operatingSystemName,
      TrustedAclPrincipalMatcherSource matcherSource)
      throws IOException {
    UserPrincipal checkedCandidate = Objects.requireNonNull(candidate, "candidate");
    String checkedOperatingSystemName =
        Objects.requireNonNull(operatingSystemName, "operatingSystemName");
    TrustedAclPrincipalMatcherSource checkedMatcherSource =
        Objects.requireNonNull(matcherSource, "matcherSource");
    if (!isWindows(checkedOperatingSystemName)) {
      return false;
    }
    TrustedAclPrincipalMatcher matcher =
        Objects.requireNonNull(checkedMatcherSource.acquire(), "trusted ACL principal matcher");
    try {
      return matcher.matchesTrusted(checkedCandidate);
    } finally {
      matcher.release();
    }
  }

  /**
   * Returns the ACL owner and, on Windows, the observed ACL principal that is the current token
   * user. The token matcher resolves the observed principal and current token to native SIDs, so a
   * display-name spelling cannot authorize ACL mutation.
   */
  static List<UserPrincipal> permittedAclMutationPrincipalsForCreation(
      String operatingSystemName,
      PrivateOutputDirectory.AclState aclState,
      CurrentTokenUserPrincipalMatcherSource tokenUserMatcherSource)
      throws IOException {
    PrivateOutputDirectory.AclState checkedAclState = Objects.requireNonNull(aclState, "aclState");
    UserPrincipal aclOwner = checkedAclState.owner();
    if (!isWindows(operatingSystemName)) {
      return List.of(aclOwner);
    }

    CurrentTokenUserPrincipalMatcherSource checkedMatcherSource =
        Objects.requireNonNull(tokenUserMatcherSource, "tokenUserMatcherSource");
    CurrentTokenUserPrincipalMatcher matcher =
        Objects.requireNonNull(checkedMatcherSource.acquire(), "current-token user matcher");
    try {
      List<UserPrincipal> observedPrincipals = new ArrayList<>();
      observedPrincipals.add(aclOwner);
      checkedAclState.entries().forEach(entry -> observedPrincipals.add(entry.principal()));
      for (UserPrincipal observedPrincipal : observedPrincipals) {
        UserPrincipal checkedPrincipal = Objects.requireNonNull(observedPrincipal, "ACL principal");
        if (matcher.matchesCurrentToken(checkedPrincipal)) {
          return aclOwner.equals(checkedPrincipal)
              ? List.of(aclOwner)
              : List.of(aclOwner, checkedPrincipal);
        }
      }
    } finally {
      matcher.release();
    }
    return List.of(aclOwner);
  }

  static PrivateOutputDirectory.AclMutationPrincipalKind classifyUntrustedForCurrentPlatform(
      java.nio.file.Path path, UserPrincipal candidate) throws IOException {
    java.nio.file.Path checkedPath = Objects.requireNonNull(path, "path");
    UserPrincipal checkedCandidate = Objects.requireNonNull(candidate, "candidate");
    return classifyUntrustedForOperatingSystem(
        System.getProperty("os.name", ""),
        checkedCandidate,
        checkedPath.getFileSystem().getUserPrincipalLookupService()::lookupPrincipalByName);
  }

  static PrivateOutputDirectory.AclMutationPrincipalKind classifyUntrustedForOperatingSystem(
      String operatingSystemName, UserPrincipal candidate, PrincipalLookup lookup)
      throws IOException {
    if (!isWindows(operatingSystemName)) {
      return PrivateOutputDirectory.AclMutationPrincipalKind.OTHER;
    }
    return classifyUntrusted(
        Objects.requireNonNull(candidate, "candidate"), Objects.requireNonNull(lookup, "lookup"));
  }

  static PrivateOutputDirectory.AclMutationPrincipalKind classifyUntrusted(
      UserPrincipal candidate, PrincipalLookup lookup) throws IOException {
    UserPrincipal checkedCandidate = Objects.requireNonNull(candidate, "candidate");
    PrincipalLookup checkedLookup = Objects.requireNonNull(lookup, "lookup");
    for (KnownUntrustedPrincipal knownPrincipal : KNOWN_UNTRUSTED_PRINCIPALS) {
      if (matchesKnownPrincipal(checkedCandidate, knownPrincipal.identifiers(), checkedLookup)) {
        return knownPrincipal.kind();
      }
    }
    return PrivateOutputDirectory.AclMutationPrincipalKind.OTHER;
  }

  static boolean isWindows(String operatingSystemName) {
    return Objects.requireNonNull(operatingSystemName, "operatingSystemName")
        .toLowerCase(Locale.ROOT)
        .contains("windows");
  }

  private static boolean matchesKnownPrincipal(
      UserPrincipal candidate, List<String> identifiers, PrincipalLookup lookup)
      throws IOException {
    for (String identifier : identifiers) {
      try {
        if (candidate.equals(lookup.lookup(identifier))) {
          return true;
        }
      } catch (UserPrincipalNotFoundException ignored) {
        // Try the next stable or canonical identifier without disclosing the candidate identity.
      }
    }
    return false;
  }

  private record KnownUntrustedPrincipal(
      PrivateOutputDirectory.AclMutationPrincipalKind kind, List<String> identifiers) {}

  /** Resolves one well-known Windows identifier to the filesystem's SID-backed principal. */
  @FunctionalInterface
  interface PrincipalLookup {
    /** Returns the principal represented by one stable SID or well-known account identifier. */
    UserPrincipal lookup(String identifier) throws IOException;
  }

  /** Acquires one current-token matcher for a complete private-directory ACL decision. */
  @FunctionalInterface
  interface CurrentTokenUserPrincipalMatcherSource {
    /** Acquires the native current-token matcher for this one complete ACL snapshot. */
    CurrentTokenUserPrincipalMatcher acquire() throws IOException;
  }

  /** Matches observed ACL principals to the current Windows token through native SIDs. */
  @FunctionalInterface
  interface CurrentTokenUserPrincipalMatcher {
    /** Returns whether this exact observed principal has the current process token SID. */
    boolean matchesCurrentToken(UserPrincipal principal) throws IOException;

    /** Releases the native current-token capability after its ACL decision completes. */
    default void release() throws IOException {
      // Stateless matchers retain no native capability.
    }
  }

  /** Acquires a native matcher for fixed Windows operating-system trust-boundary principals. */
  @FunctionalInterface
  interface TrustedAclPrincipalMatcherSource {
    /** Acquires one matcher for one ACL trust decision. */
    TrustedAclPrincipalMatcher acquire() throws IOException;
  }

  /** Matches observed ACL principals to fixed Windows trust-boundary security identities. */
  @FunctionalInterface
  interface TrustedAclPrincipalMatcher {
    /**
     * Returns whether this observed principal is one of the trusted operating-system principals.
     */
    boolean matchesTrusted(UserPrincipal principal) throws IOException;

    /** Releases any native matcher state after the ACL trust decision completes. */
    default void release() throws IOException {
      // Stateless matchers retain no native capability.
    }
  }
}
