package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Resolves the Windows privileged principals that form the local filesystem trust boundary. */
final class WindowsTrustedAclPrincipalResolver {
  private static final List<List<String>> TRUSTED_PRINCIPAL_IDENTIFIERS =
      List.of(
          List.of("S-1-5-18", "NT AUTHORITY\\SYSTEM"),
          List.of("S-1-5-32-544", "BUILTIN\\Administrators"));
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

  static boolean isTrustedForCurrentPlatform(UserPrincipal candidate, FileSystem filesystem)
      throws IOException {
    Objects.requireNonNull(candidate, "candidate");
    FileSystem checkedFilesystem = Objects.requireNonNull(filesystem, "filesystem");
    return isTrustedForOperatingSystem(
        candidate,
        System.getProperty("os.name", ""),
        checkedFilesystem.getUserPrincipalLookupService()::lookupPrincipalByName);
  }

  static boolean isTrustedForOperatingSystem(
      UserPrincipal candidate, String operatingSystemName, PrincipalLookup lookup)
      throws IOException {
    return isWindows(operatingSystemName) && isTrusted(candidate, lookup);
  }

  static boolean isTrustedForCurrentPlatform(java.nio.file.Path path, UserPrincipal candidate)
      throws IOException {
    java.nio.file.Path checkedPath = Objects.requireNonNull(path, "path");
    return isTrustedForCurrentPlatform(
        Objects.requireNonNull(candidate, "candidate"), checkedPath.getFileSystem());
  }

  /**
   * Resolves the current Windows token user through its native SID, never through an account name.
   */
  static UserPrincipal resolveCurrentTokenUserForCurrentPlatform(Path path) throws IOException {
    Path checkedPath = Objects.requireNonNull(path, "path");
    return resolveCurrentTokenUser(
        System.getProperty("os.name", ""),
        checkedPath.getFileSystem().getUserPrincipalLookupService()::lookupPrincipalByName,
        WindowsTrustedAclPrincipalResolver::readCurrentTokenUserSid);
  }

  static UserPrincipal resolveCurrentTokenUser(
      String operatingSystemName,
      PrincipalLookup lookup,
      CurrentTokenUserSidSource tokenUserSidSource)
      throws IOException {
    if (!isWindows(operatingSystemName)) {
      throw new IOException("A Windows token user can only resolve on Windows.");
    }
    String sid =
        Objects.requireNonNull(tokenUserSidSource, "tokenUserSidSource").currentTokenUserSid();
    if (!sid.matches("S-1-(?:[0-9]+-)*[0-9]+")) {
      throw new IOException("Windows returned a noncanonical current token-user SID.");
    }
    return Objects.requireNonNull(lookup, "lookup").lookup(sid);
  }

  static PrivateOutputDirectory.AclMutationPrincipalKind classifyUntrustedForCurrentPlatform(
      java.nio.file.Path path, UserPrincipal candidate) throws IOException {
    java.nio.file.Path checkedPath = Objects.requireNonNull(path, "path");
    UserPrincipal checkedCandidate = Objects.requireNonNull(candidate, "candidate");
    if (!isWindows(System.getProperty("os.name", ""))) {
      return PrivateOutputDirectory.AclMutationPrincipalKind.OTHER;
    }
    PrincipalLookup lookup =
        checkedPath.getFileSystem().getUserPrincipalLookupService()::lookupPrincipalByName;
    return classifyUntrusted(checkedCandidate, lookup);
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

  static boolean isTrusted(UserPrincipal candidate, PrincipalLookup lookup) throws IOException {
    UserPrincipal checkedCandidate = Objects.requireNonNull(candidate, "candidate");
    PrincipalLookup checkedLookup = Objects.requireNonNull(lookup, "lookup");
    for (List<String> identifiers : TRUSTED_PRINCIPAL_IDENTIFIERS) {
      UserPrincipal trusted = resolveTrustedPrincipal(identifiers, checkedLookup);
      if (checkedCandidate.equals(trusted)) {
        return true;
      }
    }
    return false;
  }

  private static UserPrincipal resolveTrustedPrincipal(
      List<String> identifiers, PrincipalLookup lookup) throws IOException {
    for (String identifier : identifiers) {
      try {
        return lookup.lookup(identifier);
      } catch (UserPrincipalNotFoundException ignored) {
        // The SID form is preferred; the canonical well-known account name is its fallback.
      }
    }
    throw new IOException(
        "Windows could not resolve a required trusted operating-system ACL principal.");
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

  private static String readCurrentTokenUserSid() throws IOException {
    try (WindowsPrivateOutputFileOwner owner =
        WindowsPrivateOutputFileOwner.acquire(
            WindowsPrivateOutputFileBindingSupport.nativeRuntime().calls())) {
      return owner.ownerSidText();
    }
  }

  private record KnownUntrustedPrincipal(
      PrivateOutputDirectory.AclMutationPrincipalKind kind, List<String> identifiers) {}

  /** Resolves one well-known Windows identifier to the filesystem's SID-backed principal. */
  @FunctionalInterface
  interface PrincipalLookup {
    /** Returns the principal represented by one stable SID or well-known account identifier. */
    UserPrincipal lookup(String identifier) throws IOException;
  }

  /** Supplies the current token user's stable SID without disclosing an account display name. */
  @FunctionalInterface
  interface CurrentTokenUserSidSource {
    /** Returns the canonical SID of the current Windows token user. */
    String currentTokenUserSid() throws IOException;
  }
}
