package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Reads production filesystem facts for private-output admission. */
final class NioPrivateOutputDirectoryFilesystemAccess
    implements PrivateOutputDirectory.FilesystemAccess {
  private static final int UNIX_STICKY_BIT = 0x200;
  private static final WindowsTrustedAclPrincipalResolver.CurrentTokenUserPrincipalMatcherSource
      CURRENT_TOKEN_USER_PRINCIPAL_MATCHER_SOURCE =
          WindowsPrivateOutputFilePlatformAdapter.PRODUCTION
              ::acquireCurrentTokenAclPrincipalMatcher;
  private static final WindowsTrustedAclPrincipalResolver.TrustedAclPrincipalMatcherSource
      TRUSTED_ACL_PRINCIPAL_MATCHER_SOURCE =
          () -> WindowsPrivateOutputFilePlatformAdapter.PRODUCTION::matchesTrustedAclPrincipal;
  private final PosixAttributesReader posixAttributesReader;
  private final UnixAttributeReader unixAttributeReader;
  private final AclViewReader aclViewReader;

  NioPrivateOutputDirectoryFilesystemAccess() {
    this(
        path -> Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS),
        (path, attribute) -> Files.getAttribute(path, attribute, LinkOption.NOFOLLOW_LINKS),
        path ->
            Files.getFileAttributeView(
                path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS));
  }

  NioPrivateOutputDirectoryFilesystemAccess(
      PosixAttributesReader posixAttributesReader,
      UnixAttributeReader unixAttributeReader,
      AclViewReader aclViewReader) {
    this.posixAttributesReader =
        Objects.requireNonNull(posixAttributesReader, "posixAttributesReader");
    this.unixAttributeReader = Objects.requireNonNull(unixAttributeReader, "unixAttributeReader");
    this.aclViewReader = Objects.requireNonNull(aclViewReader, "aclViewReader");
  }

  @Override
  public boolean isDirectoryNoFollow(Path path) {
    return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
  }

  @Override
  public PrivateOutputDirectory.NoFollowEntryKind noFollowEntryKind(Path path) throws IOException {
    try {
      BasicFileAttributes attributes =
          Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      return attributes.isDirectory()
          ? PrivateOutputDirectory.NoFollowEntryKind.DIRECTORY
          : PrivateOutputDirectory.NoFollowEntryKind.OTHER;
    } catch (NoSuchFileException missing) {
      return PrivateOutputDirectory.NoFollowEntryKind.MISSING;
    }
  }

  @Override
  public boolean supportsPosix(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains("posix");
  }

  @Override
  public boolean supportsAcl(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains("acl");
  }

  @Override
  public Path toRealPath(Path path) throws IOException {
    return path.toRealPath();
  }

  @Override
  public @Nullable Path parent(Path path) {
    return path.getParent();
  }

  @Override
  public Set<PosixFilePermission> readPosixPermissions(Path path) throws IOException {
    return Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
  }

  @Override
  public PrivateOutputDirectory.PosixDirectoryIdentity readPosixDirectoryIdentity(Path path)
      throws IOException {
    PosixFileAttributes attributes = posixAttributesReader.read(path);
    Object modeAttribute = unixAttributeReader.read(path, "unix:mode");
    if (!(modeAttribute instanceof Number mode)) {
      throw new IOException(
          "The filesystem did not provide a UNIX mode for the output directory ancestry.");
    }
    Object userIdAttribute = unixAttributeReader.read(path, "unix:uid");
    if (!(userIdAttribute instanceof Number userId)) {
      throw new IOException(
          "The filesystem did not provide a UNIX user identifier for the output directory ancestry.");
    }
    return new PrivateOutputDirectory.PosixDirectoryIdentity(
        attributes.owner(), userId.longValue(), (mode.intValue() & UNIX_STICKY_BIT) != 0);
  }

  @Override
  public PrivateOutputDirectory.AclState readAcl(Path path) throws IOException {
    @Nullable AclFileAttributeView view = aclViewReader.read(path);
    if (view == null) {
      throw new IOException("The filesystem did not provide an ACL view for the output directory.");
    }
    return new PrivateOutputDirectory.AclState(view.getOwner(), view.getAcl());
  }

  @Override
  public boolean isTrustedAclMutationPrincipal(Path path, UserPrincipal principal)
      throws IOException {
    return WindowsTrustedAclPrincipalResolver.isTrustedForCurrentPlatform(
        Objects.requireNonNull(principal, "principal"),
        productionTrustedAclPrincipalMatcherSource());
  }

  @Override
  public boolean isCurrentTokenAclPrincipal(Path path, UserPrincipal principal) throws IOException {
    Objects.requireNonNull(path, "path");
    return WindowsTrustedAclPrincipalResolver.isCurrentTokenForCurrentPlatform(
        Objects.requireNonNull(principal, "principal"),
        CURRENT_TOKEN_USER_PRINCIPAL_MATCHER_SOURCE);
  }

  @Override
  public boolean matchesAclPrincipalIdentity(
      Path path, UserPrincipal firstPrincipal, UserPrincipal secondPrincipal) throws IOException {
    return matchesAclPrincipalIdentity(
        System.getProperty("os.name", ""),
        Objects.requireNonNull(path, "path"),
        Objects.requireNonNull(firstPrincipal, "firstPrincipal"),
        Objects.requireNonNull(secondPrincipal, "secondPrincipal"),
        WindowsPrivateOutputFilePlatformAdapter.PRODUCTION::matchesAclPrincipalIdentity);
  }

  static boolean matchesAclPrincipalIdentity(
      String operatingSystemName,
      Path path,
      UserPrincipal firstPrincipal,
      UserPrincipal secondPrincipal,
      AclPrincipalIdentityMatcher identityMatcher)
      throws IOException {
    String checkedOperatingSystemName =
        Objects.requireNonNull(operatingSystemName, "operatingSystemName");
    Objects.requireNonNull(path, "path");
    UserPrincipal checkedFirstPrincipal = Objects.requireNonNull(firstPrincipal, "firstPrincipal");
    UserPrincipal checkedSecondPrincipal =
        Objects.requireNonNull(secondPrincipal, "secondPrincipal");
    AclPrincipalIdentityMatcher checkedIdentityMatcher =
        Objects.requireNonNull(identityMatcher, "identityMatcher");
    if (WindowsTrustedAclPrincipalResolver.isWindows(checkedOperatingSystemName)) {
      return checkedIdentityMatcher.matches(checkedFirstPrincipal, checkedSecondPrincipal);
    }
    return checkedFirstPrincipal.equals(checkedSecondPrincipal);
  }

  @Override
  public List<UserPrincipal> permittedAclMutationPrincipalsForCreation(
      Path path, PrivateOutputDirectory.AclState aclState) throws IOException {
    return permittedAclMutationPrincipalsForCreation(
        System.getProperty("os.name", ""),
        Objects.requireNonNull(path, "path"),
        Objects.requireNonNull(aclState, "aclState"),
        CURRENT_TOKEN_USER_PRINCIPAL_MATCHER_SOURCE);
  }

  static List<UserPrincipal> permittedAclMutationPrincipalsForCreation(
      String operatingSystemName,
      Path path,
      PrivateOutputDirectory.AclState aclState,
      WindowsTrustedAclPrincipalResolver.CurrentTokenUserPrincipalMatcherSource
          tokenUserMatcherSource)
      throws IOException {
    Objects.requireNonNull(path, "path");
    return WindowsTrustedAclPrincipalResolver.permittedAclMutationPrincipalsForCreation(
        operatingSystemName,
        Objects.requireNonNull(aclState, "aclState"),
        Objects.requireNonNull(tokenUserMatcherSource, "tokenUserMatcherSource"));
  }

  static WindowsTrustedAclPrincipalResolver.TrustedAclPrincipalMatcherSource
      productionTrustedAclPrincipalMatcherSource() {
    return TRUSTED_ACL_PRINCIPAL_MATCHER_SOURCE;
  }

  @Override
  public PrivateOutputDirectory.AclMutationPrincipalKind classifyAclMutationPrincipal(
      Path path, UserPrincipal principal) throws IOException {
    return WindowsTrustedAclPrincipalResolver.classifyUntrustedForCurrentPlatform(
        Objects.requireNonNull(path, "path"), Objects.requireNonNull(principal, "principal"));
  }

  /** Reads POSIX attributes for one output-directory path. */
  @FunctionalInterface
  interface PosixAttributesReader {
    /** Returns the POSIX attributes for the selected path. */
    PosixFileAttributes read(Path path) throws IOException;
  }

  /** Reads a UNIX implementation attribute for one output-directory path. */
  @FunctionalInterface
  interface UnixAttributeReader {
    /** Returns the selected UNIX attribute value. */
    Object read(Path path, String attribute) throws IOException;
  }

  /** Reads the ACL attribute view for one output-directory path. */
  @FunctionalInterface
  interface AclViewReader {
    /** Returns the ACL view, or {@code null} when the filesystem does not provide one. */
    @Nullable AclFileAttributeView read(Path path) throws IOException;
  }

  /** Compares two ACL principal identities on the active filesystem platform. */
  @FunctionalInterface
  interface AclPrincipalIdentityMatcher {
    /** Returns whether both observed principals prove the same native identity. */
    boolean matches(UserPrincipal firstPrincipal, UserPrincipal secondPrincipal) throws IOException;
  }
}
