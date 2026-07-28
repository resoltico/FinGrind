package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Creates and validates owner-only POSIX coordination files and physical object identities. */
final class SqlitePosixCoordinationFileSecurity {
  private static final Set<PosixFilePermission> CONTROL_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private SqlitePosixCoordinationFileSecurity() {}

  static FileChannel openNewOwnerOnlyProtocolFile(Path protocolPath) throws IOException {
    Path checkedPath = Objects.requireNonNull(protocolPath, "protocolPath");
    SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(checkedPath);
    requireAtomicControlFileCreationCapability(checkedPath);
    return createAtomicallySecureAndOpen(checkedPath);
  }

  static FileChannel openExistingSecureControlFile(Path controlPath) throws IOException {
    Path checkedPath = Objects.requireNonNull(controlPath, "controlPath");
    SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(checkedPath);
    requireAtomicControlFileCreationCapability(checkedPath);
    requireSecureRegularFile(checkedPath);
    try {
      return FileChannel.open(
          checkedPath,
          StandardOpenOption.READ,
          StandardOpenOption.WRITE,
          LinkOption.NOFOLLOW_LINKS);
    } catch (UnsupportedOperationException unsupported) {
      throw atomicOwnerOnlyProtocolFileCreationUnsupported(checkedPath, unsupported);
    }
  }

  static String physicalObjectIdentity(Path existingArtifactPath) throws IOException {
    return physicalObjectIdentity(
        existingArtifactPath,
        path -> Files.readAttributes(path, "unix:dev,ino", LinkOption.NOFOLLOW_LINKS));
  }

  /**
   * Reads one POSIX physical identity through an explicit provider boundary.
   *
   * <p>The boundary makes malformed provider attribute responses fail closed under the same
   * contract as a real filesystem response.
   */
  static String physicalObjectIdentity(
      Path existingArtifactPath, PosixIdentityAttributeReader attributeReader) throws IOException {
    Path checkedPath = requireExistingRegularArtifact(existingArtifactPath);
    PosixIdentityAttributeReader checkedReader =
        Objects.requireNonNull(attributeReader, "attributeReader");
    if (!SqliteBookFilesystemSupport.supportsPosix(checkedPath)) {
      throw new IOException(
          "FinGrind physical-object coordination requires explicit POSIX device/inode identity or a Windows native handle.");
    }
    Map<String, Object> attributes = checkedReader.read(checkedPath);
    Object device = attributes.get("dev");
    Object inode = attributes.get("ino");
    if (!(device instanceof Number deviceNumber) || !(inode instanceof Number inodeNumber)) {
      throw new IOException(
          "The selected filesystem did not expose explicit POSIX device/inode identity.");
    }
    return "posix-v1:dev="
        + Long.toUnsignedString(deviceNumber.longValue())
        + ":ino="
        + Long.toUnsignedString(inodeNumber.longValue());
  }

  /** Reads the exact POSIX identity attributes exposed by one filesystem provider. */
  @FunctionalInterface
  interface PosixIdentityAttributeReader {
    Map<String, Object> read(Path path) throws IOException;
  }

  private static Path requireExistingRegularArtifact(Path existingArtifactPath) throws IOException {
    Path checkedPath =
        Objects.requireNonNull(existingArtifactPath, "existingArtifactPath")
            .toAbsolutePath()
            .normalize();
    if (!Files.isRegularFile(checkedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(
          "FinGrind physical-object coordination requires one existing regular non-symlink artifact: "
              + checkedPath
              + ".");
    }
    return checkedPath;
  }

  private static void requireAtomicControlFileCreationCapability(Path controlPath) {
    if (SqliteBookKeyFileSecuritySupport.supportsPosix(controlPath)) {
      return;
    }
    throw atomicOwnerOnlyProtocolFileCreationUnsupported(controlPath, null);
  }

  private static SqliteCallerPathContractException atomicOwnerOnlyProtocolFileCreationUnsupported(
      Path controlPath, @Nullable Throwable cause) {
    String message =
        "The selected filesystem cannot atomically create one owner-only FinGrind protocol file.";
    return cause == null
        ? new SqliteCallerPathContractException(
            controlPath,
            SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
            message)
        : new SqliteCallerPathContractException(
            controlPath,
            SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
            message,
            cause);
  }

  private static FileChannel createAtomicallySecureAndOpen(Path controlPath) throws IOException {
    try {
      return FileChannel.open(
          controlPath,
          Set.<OpenOption>of(
              StandardOpenOption.READ,
              StandardOpenOption.WRITE,
              StandardOpenOption.CREATE_NEW,
              LinkOption.NOFOLLOW_LINKS),
          PosixFilePermissions.asFileAttribute(CONTROL_FILE_PERMISSIONS));
    } catch (UnsupportedOperationException unsupported) {
      throw atomicOwnerOnlyProtocolFileCreationUnsupported(controlPath, unsupported);
    }
  }

  private static void requireSecureRegularFile(Path controlPath) throws IOException {
    if (!Files.isRegularFile(controlPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(
          "FinGrind coordination state must remain one regular non-symlink file.");
    }
    switch (SqliteBookKeyFileSecurity.requireSecureKeyFile(controlPath)) {
      case ContractDecision.Accepted<Path> _ -> {}
      case ContractDecision.Rejected<Path> _ ->
          throw new IOException("FinGrind coordination state must remain owner-only.");
    }
  }
}
