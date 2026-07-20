package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Atomically publishes one fully written encrypted key file without replacing an existing path. */
final class AttestationKeyFilePublication {
  private static final String STAGED_KEY_FILE_PREFIX = ".fingrind-attestation-key-";
  private static final String STAGED_KEY_FILE_SUFFIX = ".tmp";
  private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS =
      Set.copyOf(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

  private AttestationKeyFilePublication() {}

  static void writeNewKeyFile(Path path, byte[] encryptedPrivateKey) throws IOException {
    writeNewKeyFile(
        path,
        encryptedPrivateKey,
        AttestationKeyFilePublication::writeAndForce,
        Files::createLink,
        Files::deleteIfExists);
  }

  static void writeNewKeyFile(
      Path path,
      byte[] encryptedPrivateKey,
      StagedKeyFileWriter stagedKeyFileWriter,
      NoReplaceLinkCreator noReplaceLinkCreator,
      PathDeleter stagedKeyFileDeleter)
      throws IOException {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(encryptedPrivateKey, "encryptedPrivateKey");
    Objects.requireNonNull(stagedKeyFileWriter, "stagedKeyFileWriter");
    Objects.requireNonNull(noReplaceLinkCreator, "noReplaceLinkCreator");
    Objects.requireNonNull(stagedKeyFileDeleter, "stagedKeyFileDeleter");
    Path stagedPath = createOwnerOnlyTemporaryFile(parentDirectory(path));
    try {
      stagedKeyFileWriter.write(stagedPath, encryptedPrivateKey);
      noReplaceLinkCreator.create(path, stagedPath);
    } catch (IOException | RuntimeException | Error exception) {
      discardStagedKeyFile(stagedPath, exception, stagedKeyFileDeleter);
      throw exception;
    }
    stagedKeyFileDeleter.delete(stagedPath);
  }

  static Path createOwnerOnlyTemporaryFile(Path parent, TemporaryKeyFileCreator ownerOnlyCreator)
      throws IOException {
    Objects.requireNonNull(parent, "parent");
    try {
      return Objects.requireNonNull(ownerOnlyCreator, "ownerOnlyCreator").create(parent);
    } catch (UnsupportedOperationException exception) {
      return Files.createTempFile(parent, STAGED_KEY_FILE_PREFIX, STAGED_KEY_FILE_SUFFIX);
    }
  }

  static void writeFully(ByteBuffer source, ByteBufferWriter writer) throws IOException {
    while (source.hasRemaining()) {
      if (writer.write(source) <= 0) {
        throw new IOException("Failed to write the complete encrypted attestation key file.");
      }
    }
  }

  private static void writeAndForce(Path path, byte[] encryptedPrivateKey) throws IOException {
    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
      ByteBuffer source = ByteBuffer.wrap(encryptedPrivateKey);
      writeFully(source, channel::write);
      channel.force(true);
    }
  }

  private static Path parentDirectory(Path path) {
    Path parent = path.toAbsolutePath().normalize().getParent();
    if (parent == null) {
      throw new IllegalArgumentException("Attestation key file must have a parent directory.");
    }
    return parent;
  }

  private static Path createOwnerOnlyTemporaryFile(Path parent) throws IOException {
    return createOwnerOnlyTemporaryFile(
        parent,
        directory ->
            Files.createTempFile(
                directory,
                STAGED_KEY_FILE_PREFIX,
                STAGED_KEY_FILE_SUFFIX,
                PosixFilePermissions.asFileAttribute(OWNER_ONLY_PERMISSIONS)));
  }

  private static void discardStagedKeyFile(
      Path stagedPath, Throwable failure, PathDeleter stagedKeyFileDeleter) {
    try {
      stagedKeyFileDeleter.delete(stagedPath);
    } catch (IOException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }

  /** Writes encrypted key bytes to an already-created file. */
  @FunctionalInterface
  interface ByteBufferWriter {
    /** Writes from the supplied source buffer and returns the byte count. */
    int write(ByteBuffer source) throws IOException;
  }

  /** Writes one fully encrypted key file to a private stage. */
  @FunctionalInterface
  interface StagedKeyFileWriter {
    /** Writes the encrypted private key and makes the staged bytes durable. */
    void write(Path stagedPath, byte[] encryptedPrivateKey) throws IOException;
  }

  /** Atomically creates the final path as a hard link without replacement. */
  @FunctionalInterface
  interface NoReplaceLinkCreator {
    /** Links the completed staged key file to the absent final path. */
    void create(Path finalPath, Path stagedPath) throws IOException;
  }

  /** Deletes one staging-file path after a failed or completed publication. */
  @FunctionalInterface
  interface PathDeleter {
    /** Removes the selected staged key-file path. */
    void delete(Path stagedPath) throws IOException;
  }

  /** Creates an owner-only temporary key file in the selected directory. */
  @FunctionalInterface
  interface TemporaryKeyFileCreator {
    /** Returns a newly created temporary key-file path. */
    Path create(Path parent) throws IOException;
  }
}
