package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CryptographicPrimitives;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * POSIX-only secure creation and exact-channel validation for managed-library snapshots.
 *
 * <p>ACL-only filesystems are deliberately refused. Java exposes no handle-bound ACL creation
 * primitive, so create-then-repair would allow a same-owner pathname replacement before the ACL
 * write. Every retained snapshot destination is instead created once as {@code 0600} through its
 * own {@code CREATE_NEW + NOFOLLOW_LINKS} channel under an atomically private {@code 0700}
 * directory.
 */
final class SqliteManagedLibrarySnapshotSecurity {
  private static final String SNAPSHOT_DIRECTORY_PREFIX = "fingrind-managed-sqlite-";
  private static final int COPY_BUFFER_BYTES = 16 * 1024;
  private static final int MAXIMUM_CHECKSUM_BYTES = 64 * 1024;
  private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private SqliteManagedLibrarySnapshotSecurity() {}

  static Path createPrivateSnapshotDirectory() {
    Path configuredTempRoot =
        SqliteManagedLibraryDigestSupport.normalizedLibraryPath(
            Path.of(System.getProperty("java.io.tmpdir")));
    Path tempRoot = canonicalExistingDirectory(configuredTempRoot);
    requirePosixSnapshotFilesystem(tempRoot);
    return createPrivateSnapshotDirectoryOnPosix(tempRoot);
  }

  /** Same-package capability seam for filesystem-contract tests. */
  static Path createPrivateSnapshotDirectory(Path tempRoot, boolean supportsPosix) {
    Path normalizedTempRoot =
        canonicalExistingDirectory(
            SqliteManagedLibraryDigestSupport.normalizedLibraryPath(tempRoot));
    if (!supportsPosix) {
      throw posixSnapshotFilesystemRequired(normalizedTempRoot);
    }
    return createPrivateSnapshotDirectoryOnPosix(normalizedTempRoot);
  }

  /** Opens one verified source through a descriptor that will not follow a final symlink. */
  static FileChannel openSourceReadChannel(Path sourcePath) throws IOException {
    Path checkedSourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
    if (!Files.isRegularFile(checkedSourcePath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(
          "Managed SQLite snapshot source must remain one regular non-symlink file: "
              + checkedSourcePath
              + ".");
    }
    try {
      return FileChannel.open(
          checkedSourcePath, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    } catch (UnsupportedOperationException unsupported) {
      throw new ManagedSqliteRuntimeUnavailableException(
          "FinGrind cannot open a managed SQLite snapshot source without nofollow protection at "
              + checkedSourcePath
              + ".",
          unsupported);
    }
  }

  /**
   * Atomically creates one exact private snapshot destination and returns its bound channel.
   *
   * <p>The caller must keep this channel open through copy, force, and validation; it must never
   * reopen the destination by pathname to repair permissions or inspect copied bytes.
   */
  static FileChannel openNewPrivateSnapshotChannel(Path snapshotPath) throws IOException {
    Path checkedSnapshotPath = Objects.requireNonNull(snapshotPath, "snapshotPath");
    Path parentDirectory =
        Objects.requireNonNull(checkedSnapshotPath.getParent(), "snapshotPath parent directory");
    requirePosixSnapshotFilesystem(parentDirectory);
    try {
      return FileChannel.open(
          checkedSnapshotPath,
          Set.<OpenOption>of(
              StandardOpenOption.READ,
              StandardOpenOption.WRITE,
              StandardOpenOption.CREATE_NEW,
              LinkOption.NOFOLLOW_LINKS),
          PosixFilePermissions.asFileAttribute(PRIVATE_FILE_PERMISSIONS));
    } catch (UnsupportedOperationException unsupported) {
      throw posixSnapshotFilesystemRequired(checkedSnapshotPath, unsupported);
    }
  }

  /**
   * Streams source bytes into one exact created destination, forces them, and compares both
   * descriptor-bound digests before returning the destination digest.
   */
  static String copyForceAndVerifyExact(FileChannel source, FileChannel destination)
      throws IOException {
    FileChannel checkedSource = Objects.requireNonNull(source, "source");
    FileChannel checkedDestination = Objects.requireNonNull(destination, "destination");
    if (checkedDestination.size() != 0L) {
      throw new IOException("A newly created managed SQLite snapshot file was not empty.");
    }
    checkedSource.position(0L);
    checkedDestination.position(0L);
    ByteBuffer buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES);
    while (true) {
      int read = checkedSource.read(buffer);
      if (read < 0) {
        break;
      }
      if (read == 0) {
        throw new IOException("Managed SQLite snapshot source did not make read progress.");
      }
      buffer.flip();
      writeFully(checkedDestination, buffer);
      buffer.clear();
    }
    checkedSource.position(0L);
    byte[] copiedSourceDigest =
        CryptographicPrimitives.sha256(Channels.newInputStream(checkedSource));
    checkedDestination.force(true);
    checkedDestination.position(0L);
    byte[] exactDestinationDigest =
        CryptographicPrimitives.sha256(Channels.newInputStream(checkedDestination));
    if (!CryptographicPrimitives.constantTimeEquals(copiedSourceDigest, exactDestinationDigest)) {
      throw new IOException(
          "Managed SQLite snapshot bytes changed before exact-channel validation.");
    }
    return HexFormat.of().formatHex(exactDestinationDigest);
  }

  /** Reads one forced checksum snapshot through the exact channel that created it. */
  static List<String> readUtf8LinesFromExactChannel(FileChannel checksumChannel)
      throws IOException {
    FileChannel checkedChecksumChannel = Objects.requireNonNull(checksumChannel, "checksumChannel");
    long size = checkedChecksumChannel.size();
    if (size > MAXIMUM_CHECKSUM_BYTES) {
      throw new IOException("Managed SQLite snapshot checksum exceeds its maximum size.");
    }
    ByteBuffer contents = ByteBuffer.allocate(Math.toIntExact(size));
    checkedChecksumChannel.position(0L);
    while (contents.hasRemaining()) {
      int read = checkedChecksumChannel.read(contents);
      if (read < 0) {
        throw new IOException("Managed SQLite snapshot checksum ended unexpectedly.");
      }
      if (read == 0) {
        throw new IOException("Managed SQLite snapshot checksum did not make read progress.");
      }
    }
    return new String(contents.array(), StandardCharsets.UTF_8).lines().toList();
  }

  private static Path createPrivateSnapshotDirectoryOnPosix(Path normalizedTempRoot) {
    try {
      return Files.createTempDirectory(
          normalizedTempRoot,
          SNAPSHOT_DIRECTORY_PREFIX,
          PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY_PERMISSIONS));
    } catch (UnsupportedOperationException unsupported) {
      throw posixSnapshotFilesystemRequired(normalizedTempRoot, unsupported);
    } catch (IOException exception) {
      throw new ManagedSqliteRuntimeUnavailableException(
          "FinGrind could not create a private managed SQLite verification snapshot directory at "
              + normalizedTempRoot
              + ".",
          exception);
    }
  }

  private static Path canonicalExistingDirectory(Path configuredTempRoot) {
    try {
      return configuredTempRoot.toRealPath();
    } catch (IOException exception) {
      throw new ManagedSqliteRuntimeUnavailableException(
          "FinGrind could not resolve the managed SQLite verification snapshot temporary directory at "
              + configuredTempRoot
              + ".",
          exception);
    }
  }

  private static void requirePosixSnapshotFilesystem(Path path) {
    Path checkedPath = Objects.requireNonNull(path, "path");
    try {
      if (Files.getFileStore(checkedPath).supportsFileAttributeView("posix")) {
        return;
      }
    } catch (IOException exception) {
      throw posixSnapshotFilesystemRequired(checkedPath, exception);
    }
    throw posixSnapshotFilesystemRequired(checkedPath);
  }

  private static ManagedSqliteRuntimeUnavailableException posixSnapshotFilesystemRequired(
      Path path) {
    return new ManagedSqliteRuntimeUnavailableException(
        posixSnapshotFilesystemRequiredMessage(path));
  }

  private static ManagedSqliteRuntimeUnavailableException posixSnapshotFilesystemRequired(
      Path path, Throwable cause) {
    return new ManagedSqliteRuntimeUnavailableException(
        posixSnapshotFilesystemRequiredMessage(path), cause);
  }

  private static String posixSnapshotFilesystemRequiredMessage(Path path) {
    return "FinGrind cannot create a secure managed SQLite verification snapshot because the "
        + "temporary filesystem at "
        + path
        + " does not support atomic POSIX owner-only file creation. Configure java.io.tmpdir "
        + "to a POSIX filesystem and restart FinGrind.";
  }

  private static void writeFully(FileChannel destination, ByteBuffer bytes) throws IOException {
    while (bytes.hasRemaining()) {
      if (destination.write(bytes) <= 0) {
        throw new IOException("Managed SQLite snapshot destination did not make write progress.");
      }
    }
  }
}
