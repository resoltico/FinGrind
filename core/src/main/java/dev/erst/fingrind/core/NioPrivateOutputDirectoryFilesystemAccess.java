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
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Reads production filesystem facts for private-output admission. */
final class NioPrivateOutputDirectoryFilesystemAccess
    implements PrivateOutputDirectory.FilesystemAccess {
  private static final int UNIX_STICKY_BIT = 0x200;
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
}
