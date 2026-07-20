package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Atomically publishes one fully written encrypted key file without replacing an existing path. */
final class AttestationKeyFilePublication {
  private static final String STAGED_KEY_FILE_PREFIX = ".fingrind-attestation-key-";
  private static final String STAGED_KEY_FILE_SUFFIX = ".tmp";
  private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS =
      Set.copyOf(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
  private static final Set<AclEntryPermission> OWNER_ONLY_ACL_PERMISSIONS =
      Set.copyOf(EnumSet.allOf(AclEntryPermission.class));

  private AttestationKeyFilePublication() {}

  static void writeNewKeyFile(Path path, byte[] encryptedPrivateKey) throws IOException {
    writeNewKeyFile(
        path,
        encryptedPrivateKey,
        AttestationKeyFilePublication::writeAndForce,
        Files::createLink,
        Files::deleteIfExists,
        Files::deleteIfExists,
        AttestationDirectoryDurability::force);
  }

  static void writeNewKeyFile(
      Path path,
      byte[] encryptedPrivateKey,
      StagedKeyFileWriter stagedKeyFileWriter,
      NoReplaceLinkCreator noReplaceLinkCreator,
      PathDeleter stagedKeyFileDeleter)
      throws IOException {
    writeNewKeyFile(
        path,
        encryptedPrivateKey,
        stagedKeyFileWriter,
        noReplaceLinkCreator,
        stagedKeyFileDeleter,
        Files::deleteIfExists,
        AttestationDirectoryDurability::force);
  }

  static void writeNewKeyFile(
      Path path,
      byte[] encryptedPrivateKey,
      StagedKeyFileWriter stagedKeyFileWriter,
      NoReplaceLinkCreator noReplaceLinkCreator,
      PathDeleter stagedKeyFileDeleter,
      PathDeleter committedStageRetryDeleter)
      throws IOException {
    writeNewKeyFile(
        path,
        encryptedPrivateKey,
        stagedKeyFileWriter,
        noReplaceLinkCreator,
        stagedKeyFileDeleter,
        committedStageRetryDeleter,
        AttestationDirectoryDurability::force);
  }

  static void writeNewKeyFile(
      Path path,
      byte[] encryptedPrivateKey,
      StagedKeyFileWriter stagedKeyFileWriter,
      NoReplaceLinkCreator noReplaceLinkCreator,
      PathDeleter stagedKeyFileDeleter,
      PathDeleter committedStageRetryDeleter,
      DirectoryDurabilityForcer directoryDurabilityForcer)
      throws IOException {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(encryptedPrivateKey, "encryptedPrivateKey");
    Objects.requireNonNull(stagedKeyFileWriter, "stagedKeyFileWriter");
    Objects.requireNonNull(noReplaceLinkCreator, "noReplaceLinkCreator");
    Objects.requireNonNull(stagedKeyFileDeleter, "stagedKeyFileDeleter");
    Objects.requireNonNull(committedStageRetryDeleter, "committedStageRetryDeleter");
    Objects.requireNonNull(directoryDurabilityForcer, "directoryDurabilityForcer");
    Path parent = parentDirectory(path);
    Path stagedPath = createOwnerOnlyTemporaryFile(parent);
    try {
      stagedKeyFileWriter.write(stagedPath, encryptedPrivateKey);
      noReplaceLinkCreator.create(path, stagedPath);
      directoryDurabilityForcer.force(parent);
    } catch (IOException | RuntimeException | Error exception) {
      discardStagedKeyFile(stagedPath, exception, stagedKeyFileDeleter);
      throw exception;
    }
    discardCommittedStagedKeyFile(stagedPath, stagedKeyFileDeleter, committedStageRetryDeleter);
  }

  static Path createOwnerOnlyTemporaryFile(Path parent, TemporaryKeyFileCreator ownerOnlyCreator)
      throws IOException {
    return createOwnerOnlyTemporaryFile(
        parent,
        ownerOnlyCreator,
        AttestationKeyFilePublication::filesystemAclView,
        AttestationKeyFilePublication::filesystemOwner);
  }

  static Path createOwnerOnlyTemporaryFile(
      Path parent,
      TemporaryKeyFileCreator ownerOnlyCreator,
      AclViewReader aclViewReader,
      OwnerReader ownerReader)
      throws IOException {
    Objects.requireNonNull(parent, "parent");
    Objects.requireNonNull(aclViewReader, "aclViewReader");
    Objects.requireNonNull(ownerReader, "ownerReader");
    try {
      return Objects.requireNonNull(ownerOnlyCreator, "ownerOnlyCreator").create(parent);
    } catch (UnsupportedOperationException exception) {
      Path stagedPath =
          Files.createTempFile(parent, STAGED_KEY_FILE_PREFIX, STAGED_KEY_FILE_SUFFIX);
      try {
        configureOwnerOnlyAcl(stagedPath, aclViewReader, ownerReader);
        return stagedPath;
      } catch (IOException | RuntimeException | Error failure) {
        discardStagedKeyFile(stagedPath, failure, Files::deleteIfExists);
        failure.addSuppressed(exception);
        throw failure;
      }
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

  static @Nullable AclFileAttributeView filesystemAclView(Path path) {
    return Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
  }

  static UserPrincipal filesystemOwner(Path path) throws IOException {
    return Files.getOwner(path, LinkOption.NOFOLLOW_LINKS);
  }

  static void configureOwnerOnlyAcl(Path path, AclViewReader aclViewReader, OwnerReader ownerReader)
      throws IOException {
    Objects.requireNonNull(path, "path");
    AclFileAttributeView attributeView =
        Objects.requireNonNull(aclViewReader, "aclViewReader").read(path);
    if (attributeView == null) {
      throw new IOException("Attestation key files require owner-only filesystem permissions.");
    }
    UserPrincipal owner = Objects.requireNonNull(ownerReader, "ownerReader").read(path);
    configureOwnerOnlyAcl(attributeView, owner);
  }

  static void configureOwnerOnlyAcl(AclFileAttributeView attributeView, UserPrincipal owner)
      throws IOException {
    Objects.requireNonNull(attributeView, "attributeView");
    Objects.requireNonNull(owner, "owner");
    AclEntry ownerOnlyEntry =
        AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(owner)
            .setPermissions(OWNER_ONLY_ACL_PERMISSIONS)
            .build();
    attributeView.setAcl(List.of(ownerOnlyEntry));
    if (!attributeView.getAcl().equals(List.of(ownerOnlyEntry))) {
      throw new IOException("Attestation key files require owner-only filesystem permissions.");
    }
  }

  private static void discardStagedKeyFile(
      Path stagedPath, Throwable failure, PathDeleter stagedKeyFileDeleter) {
    try {
      stagedKeyFileDeleter.delete(stagedPath);
    } catch (IOException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }

  private static void discardCommittedStagedKeyFile(
      Path stagedPath, PathDeleter stagedKeyFileDeleter, PathDeleter committedStageRetryDeleter) {
    try {
      stagedKeyFileDeleter.delete(stagedPath);
    } catch (IOException cleanupFailure) {
      try {
        committedStageRetryDeleter.delete(stagedPath);
      } catch (IOException retryFailure) {
        stagedPath.toFile().deleteOnExit();
      }
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

  /**
   * Makes a parent directory's committed name changes durable before reporting publication success.
   */
  @FunctionalInterface
  interface DirectoryDurabilityForcer {
    /** Forces the committed directory entry for the selected directory to stable storage. */
    void force(Path directory) throws IOException;
  }

  /** Creates an owner-only temporary key file in the selected directory. */
  @FunctionalInterface
  interface TemporaryKeyFileCreator {
    /** Returns a newly created temporary key-file path. */
    Path create(Path parent) throws IOException;
  }

  /** Reads the ACL view for one filesystem path. */
  @FunctionalInterface
  interface AclViewReader {
    /** Returns the ACL view, or {@code null} if the filesystem does not support one. */
    @Nullable AclFileAttributeView read(Path path) throws IOException;
  }

  /** Reads the owning principal for one filesystem path. */
  @FunctionalInterface
  interface OwnerReader {
    /** Returns the principal that owns the selected filesystem path. */
    UserPrincipal read(Path path) throws IOException;
  }
}
