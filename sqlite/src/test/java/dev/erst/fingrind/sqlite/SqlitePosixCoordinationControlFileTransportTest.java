package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Behavioural coverage for the POSIX owner-only coordination-file transport. */
class SqlitePosixCoordinationControlFileTransportTest extends SqliteNativeBridgeTestSupport {
  @Test
  void controlLockRetainsOneExactRecordAndExcludesAnotherProcessLocalClaim() throws Exception {
    Path controlPath = tempDirectory.resolve("coordination.control");
    byte[] magic = SqliteCoordinationControlFiles.magic("test-control", "fixture-binding");

    SqliteCoordinationControlFiles.LockedControlFile first =
        java.util.Objects.requireNonNull(
            SqlitePosixCoordinationControlFileTransport.openOrCreateAndTryExclusiveLock(
                controlPath, magic, 0L, 1L),
            "first retained control lock");
    try {
      assertNull(
          SqlitePosixCoordinationControlFileTransport.openExistingAndTryExclusiveLock(
              controlPath, magic, 0L, 1L));
      SqlitePosixCoordinationControlFileTransport.requireExistingExactRecord(controlPath, magic);
    } finally {
      first.close();
    }

    try (SqliteCoordinationControlFiles.LockedControlFile afterRelease =
        java.util.Objects.requireNonNull(
            SqlitePosixCoordinationControlFileTransport.openExistingAndTryExclusiveLock(
                controlPath, magic, 0L, 1L),
            "control lock after first release")) {
      SqlitePosixCoordinationControlFileTransport.requireExistingExactRecord(controlPath, magic);
    }
  }

  @Test
  void immutableCoordinationRecordRejectsChangedMagicAndDuplicateCreation() throws Exception {
    Path recordPath = tempDirectory.resolve("immutable.record");
    byte[] expectedMagic = SqliteCoordinationControlFiles.magic("test-record", "expected");
    byte[] otherMagic = SqliteCoordinationControlFiles.magic("test-record", "changedx");

    SqlitePosixCoordinationControlFileTransport.createAtomicallySecureRecord(
        recordPath, expectedMagic);

    IOException changedMagic =
        assertThrows(
            IOException.class,
            () ->
                SqlitePosixCoordinationControlFileTransport.requireExistingExactRecord(
                    recordPath, otherMagic));
    assertTrue(
        java.util.Objects.requireNonNull(changedMagic.getMessage(), "changed magic message")
            .contains("magic is invalid"));
    assertThrows(
        java.nio.file.FileAlreadyExistsException.class,
        () ->
            SqlitePosixCoordinationControlFileTransport.createAtomicallySecureRecord(
                recordPath, expectedMagic));
    assertEquals(expectedMagic.length, Files.size(recordPath));
  }

  @Test
  void physicalObjectIdentityIsStableAcrossHardLinksAndRejectsAbsentArtifacts() throws Exception {
    Path original = ownerOnlyArtifact("identity/original.sqlite");
    Path alias = tempDirectory.resolve("identity-alias/alias.sqlite");
    Path aliasParent = alias.getParent();
    if (aliasParent == null) {
      throw new AssertionError("Alias fixture requires one parent directory.");
    }
    Files.createDirectories(aliasParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(aliasParent);
    Files.createLink(alias, original);

    assertEquals(
        SqlitePosixCoordinationFileSecurity.physicalObjectIdentity(original),
        SqlitePosixCoordinationFileSecurity.physicalObjectIdentity(alias));
    assertFalse(SqlitePosixCoordinationFileSecurity.physicalObjectIdentity(original).isBlank());
    assertThrows(
        IOException.class,
        () ->
            SqlitePosixCoordinationFileSecurity.physicalObjectIdentity(
                tempDirectory.resolve("identity/missing.sqlite")));
  }

  @Test
  void ownerOnlyProtocolFilesAreCreatedAtomicallyAndCanBeReopenedSecurely() throws Exception {
    Path controlPath = tempDirectory.resolve("protocol/reopen.control");
    Path parent = controlPath.getParent();
    if (parent == null) {
      throw new AssertionError("Control fixture requires one parent directory.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);

    try (FileChannel created =
        SqlitePosixCoordinationFileSecurity.openNewOwnerOnlyProtocolFile(controlPath)) {
      created.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
    }
    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(controlPath));

    try (FileChannel reopened =
        SqlitePosixCoordinationFileSecurity.openExistingSecureControlFile(controlPath)) {
      assertEquals(3L, reopened.size());
    }
    assertThrows(
        FileAlreadyExistsException.class,
        () -> SqlitePosixCoordinationFileSecurity.openNewOwnerOnlyProtocolFile(controlPath));
  }

  @Test
  void controlFileSecurityRefusesPermissiveFilesAndSymlinkSpellings() throws Exception {
    Path permissiveControl = ownerOnlyArtifact("control-security/permissive.control");
    Files.setPosixFilePermissions(
        permissiveControl,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ));

    assertThrows(
        IOException.class,
        () -> SqlitePosixCoordinationFileSecurity.openExistingSecureControlFile(permissiveControl));

    Path secureTarget = ownerOnlyArtifact("control-security/target.control");
    Path symlink = secureTarget.resolveSibling("symlink.control");
    Files.createSymbolicLink(symlink, secureTarget.getFileName());

    assertThrows(
        IOException.class,
        () -> SqlitePosixCoordinationFileSecurity.openExistingSecureControlFile(symlink));
    assertThrows(
        IOException.class,
        () -> SqlitePosixCoordinationFileSecurity.physicalObjectIdentity(symlink));
  }

  private Path ownerOnlyArtifact(String relativePath) throws IOException {
    Path artifactPath = tempDirectory.resolve(relativePath);
    Path parent = artifactPath.getParent();
    if (parent == null) {
      throw new AssertionError("Artifact fixture requires one parent directory.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    SqliteBookFileSecurity.createNewOwnerOnlyBookFile(artifactPath);
    return artifactPath;
  }
}
