package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CryptographicPrimitives;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Secure creation and exact-channel validation for managed-library snapshots.
 *
 * <p>Every retained snapshot destination is created once through the shared owner-only output
 * capability beneath an atomically private directory. The capability uses POSIX {@code 0700/0600}
 * creation or Windows protected owner-only descriptors; it never creates a readable artifact and
 * repairs access afterwards.
 */
final class SqliteManagedLibrarySnapshotSecurity {
  private static final String SNAPSHOT_DIRECTORY_PREFIX = "fingrind-managed-sqlite-";
  private static final int COPY_BUFFER_BYTES = 16 * 1024;

  private SqliteManagedLibrarySnapshotSecurity() {}

  static Path createPrivateSnapshotDirectory() {
    Path configuredTempRoot =
        SqliteManagedLibraryDigestSupport.normalizedLibraryPath(
            Path.of(System.getProperty("java.io.tmpdir")));
    Path tempRoot = canonicalExistingDirectory(configuredTempRoot);
    return createPrivateSnapshotDirectory(
        tempRoot, PrivateOutputDirectory::createNewOwnerOnlyChild);
  }

  /** Same-package capability seam for filesystem-contract tests. */
  static Path createPrivateSnapshotDirectory(
      Path tempRoot, SnapshotDirectoryCreator directoryCreator) {
    Path normalizedTempRoot =
        canonicalExistingDirectory(
            SqliteManagedLibraryDigestSupport.normalizedLibraryPath(tempRoot));
    try {
      return Objects.requireNonNull(directoryCreator, "directoryCreator")
          .create(normalizedTempRoot, SNAPSHOT_DIRECTORY_PREFIX);
    } catch (IOException | UnsupportedOperationException exception) {
      throw new ManagedSqliteRuntimeUnavailableException(
          "FinGrind could not create a private managed SQLite verification snapshot directory at "
              + normalizedTempRoot
              + ".",
          exception);
    }
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
  static PrivateOutputFile.OpenedFile openNewPrivateSnapshotChannel(Path snapshotPath)
      throws IOException {
    Path checkedSnapshotPath = Objects.requireNonNull(snapshotPath, "snapshotPath");
    try {
      return PrivateOutputFile.createNew(checkedSnapshotPath);
    } catch (PrivateOutputFile.OwnerOnlyFileViolation violation) {
      throw new ManagedSqliteRuntimeUnavailableException(
          "FinGrind could not atomically create a private managed SQLite verification snapshot at "
              + checkedSnapshotPath
              + ".",
          violation);
    }
  }

  /**
   * Streams source bytes into one exact created destination, forces them, and compares both
   * descriptor-bound digests before returning the destination digest.
   */
  static String copyForceAndVerifyExact(
      FileChannel source, PrivateOutputFile.OpenedFile destination) throws IOException {
    FileChannel checkedSource = Objects.requireNonNull(source, "source");
    PrivateOutputFile.OpenedFile checkedDestination =
        Objects.requireNonNull(destination, "destination");
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
    checkedDestination.force();
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
  static List<String> readUtf8LinesFromExactChannel(PrivateOutputFile.OpenedFile checksumChannel)
      throws IOException {
    PrivateOutputFile.OpenedFile checkedChecksumChannel =
        Objects.requireNonNull(checksumChannel, "checksumChannel");
    long size = checkedChecksumChannel.size();
    if (size > SqliteManagedLibraryDigestSupport.MAXIMUM_CHECKSUM_FILE_BYTES) {
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

  private static void writeFully(PrivateOutputFile.OpenedFile destination, ByteBuffer bytes)
      throws IOException {
    while (bytes.hasRemaining()) {
      if (destination.write(bytes) <= 0) {
        throw new IOException("Managed SQLite snapshot destination did not make write progress.");
      }
    }
  }

  /** Creates one private verification-snapshot directory under the supplied parent. */
  @FunctionalInterface
  interface SnapshotDirectoryCreator {
    /** Creates the directory with the supplied private name prefix. */
    Path create(Path parentDirectory, String namePrefix) throws IOException;
  }
}
