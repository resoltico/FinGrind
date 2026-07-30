package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.FileSystem;
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

  /** Resolves one well-known Windows identifier to the filesystem's SID-backed principal. */
  @FunctionalInterface
  interface PrincipalLookup {
    /** Returns the principal represented by one stable SID or well-known account identifier. */
    UserPrincipal lookup(String identifier) throws IOException;
  }
}
