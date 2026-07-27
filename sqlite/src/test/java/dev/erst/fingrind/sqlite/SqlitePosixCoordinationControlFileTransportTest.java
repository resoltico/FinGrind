package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
  void immutableCoordinationRecordRejectsZeroProgressDuringItsExactWriteOrRead() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\controls");
      parent.exists = true;
      parent.regularFile = false;
      parent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      byte[] magic = SqliteCoordinationControlFiles.magic("fixture", "zero-progress");

      AclFixturePath zeroWrite = fileSystem.path("\\controls\\zero-write.control");
      zeroWrite.returnZeroProgressFromNextWrite();
      IOException writeFailure =
          assertThrows(
              IOException.class,
              () ->
                  SqlitePosixCoordinationControlFileTransport.createAtomicallySecureRecord(
                      zeroWrite, magic));
      assertTrue(
          NullTestSupport.messageOf(writeFailure)
              .contains("Failed to write the complete FinGrind coordination control-file magic"));

      AclFixturePath zeroRead = fileSystem.path("\\controls\\zero-read.control");
      zeroRead.exists = true;
      zeroRead.regularFile = true;
      zeroRead.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      zeroRead.replaceContent(magic);
      zeroRead.returnZeroProgressFromNextRead();
      IOException readFailure =
          assertThrows(
              IOException.class,
              () ->
                  SqlitePosixCoordinationControlFileTransport.requireExistingExactRecord(
                      zeroRead, magic));
      assertTrue(NullTestSupport.messageOf(readFailure).contains("did not make read progress"));
    }
  }

  @Test
  void coordinationRecordsRejectNewPathsThatAlreadyContainBytes() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\controls");
      parent.exists = true;
      parent.regularFile = false;
      parent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      byte[] magic = SqliteCoordinationControlFiles.magic("fixture", "preexisting-bytes");

      AclFixturePath immutableRecord = fileSystem.path("\\controls\\record.control");
      immutableRecord.replaceContent(new byte[] {1});
      IOException recordFailure =
          assertThrows(
              IOException.class,
              () ->
                  SqlitePosixCoordinationControlFileTransport.createAtomicallySecureRecord(
                      immutableRecord, magic));
      assertTrue(
          NullTestSupport.messageOf(recordFailure).contains("coordination record was not empty"));

      AclFixturePath lockControl = fileSystem.path("\\controls\\lock.control");
      lockControl.replaceContent(new byte[] {1});
      IOException lockFailure =
          assertThrows(
              IOException.class,
              () ->
                  SqlitePosixCoordinationControlFileTransport.openOrCreateAndTryExclusiveLock(
                      lockControl, magic, 0L, 1L));
      assertTrue(
          NullTestSupport.messageOf(lockFailure)
              .contains("coordination control file was not empty"));
    }
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
  void posixCoordinationSecurityRejectsUnsupportedNofollowChannelsAndOpaqueIdentities()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\controls");
      parent.exists = true;
      parent.regularFile = false;
      parent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      UnsupportedOperationException unsupported =
          new UnsupportedOperationException("fixture cannot enforce NOFOLLOW_LINKS");

      AclFixturePath newControl = fileSystem.path("\\controls\\new.control");
      newControl.failNewFileChannelWithUnsupportedOperation(unsupported);
      SqliteCallerPathContractException newFailure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqlitePosixCoordinationFileSecurity.openNewOwnerOnlyProtocolFile(newControl));
      assertEquals(
          SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
          newFailure.pathFailure());
      assertEquals(unsupported, newFailure.getCause());

      AclFixturePath existingControl = fileSystem.path("\\controls\\existing.control");
      existingControl.exists = true;
      existingControl.regularFile = true;
      existingControl.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      existingControl.failNewFileChannelWithUnsupportedOperation(unsupported);
      SqliteCallerPathContractException existingFailure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () ->
                  SqlitePosixCoordinationFileSecurity.openExistingSecureControlFile(
                      existingControl));
      assertEquals(
          SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
          existingFailure.pathFailure());
      assertEquals(unsupported, existingFailure.getCause());

      AclFixturePath opaqueIdentity = fileSystem.path("\\controls\\opaque-identity.sqlite");
      opaqueIdentity.exists = true;
      opaqueIdentity.regularFile = true;
      IOException identityFailure =
          assertThrows(
              IOException.class,
              () -> SqlitePosixCoordinationFileSecurity.physicalObjectIdentity(opaqueIdentity));
      assertTrue(
          NullTestSupport.messageOf(identityFailure)
              .contains("did not expose explicit POSIX device/inode identity"));
    }
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

  @Test
  void controlProtocolRejectsHeaderOverlapsAndOversizedOrEmptyMagicBeforeOpeningFiles() {
    Path controlPath = tempDirectory.resolve("protocol-validation.control");
    byte[] magic = SqliteCoordinationControlFiles.magic("test-control", "geometry");

    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteCoordinationControlFiles.activitySlotPosition(-1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                controlPath,
                new byte[0],
                SqliteCoordinationControlFiles.maintenanceLockPosition(),
                1L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteCoordinationControlFiles.openExistingAndTryExclusiveLock(
                controlPath,
                new byte[(int) SqliteCoordinationControlFiles.CONTROL_LOCK_BASE],
                SqliteCoordinationControlFiles.maintenanceLockPosition(),
                1L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                controlPath, magic, SqliteCoordinationControlFiles.CONTROL_LOCK_BASE - 1L, 1L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                controlPath, magic, SqliteCoordinationControlFiles.maintenanceLockPosition(), 0L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                controlPath,
                magic,
                SqliteCoordinationControlFiles.maintenanceLockPosition(),
                Long.MAX_VALUE));
    assertFalse(Files.exists(controlPath));
  }

  @Test
  void opaqueControlHandlesReleaseAtMostOnceAndPreserveCloseFailureContext() throws Exception {
    Path controlPath = tempDirectory.resolve("opaque-handle.control");
    AtomicInteger successfulCloses = new AtomicInteger();
    SqliteCoordinationControlFiles.LockedControlFile successful =
        SqliteCoordinationControlFiles.lockedControlFile(
            controlPath, successfulCloses::incrementAndGet);

    successful.close();
    successful.close();
    assertEquals(1, successfulCloses.get());

    IOException closeCause = new IOException("injected close failure");
    AtomicInteger failingCloses = new AtomicInteger();
    SqliteCoordinationControlFiles.LockedControlFile failing =
        SqliteCoordinationControlFiles.lockedControlFile(
            controlPath,
            () -> {
              failingCloses.incrementAndGet();
              throw closeCause;
            });

    IOException failure = assertThrows(IOException.class, failing::close);
    assertEquals(closeCause, failure.getCause());
    assertEquals(1, failingCloses.get());
    failing.close();
    assertEquals(1, failingCloses.get());
  }

  @Test
  void truncatedHeadersAndInvalidTransportLockGeometryLeaveNoLeakedChannel() throws Exception {
    Path truncatedPath = tempDirectory.resolve("truncated.control");
    Path geometryPath = tempDirectory.resolve("invalid-geometry.control");
    byte[] magic = SqliteCoordinationControlFiles.magic("test-control", "truncated");

    try (FileChannel channel =
        SqlitePosixCoordinationFileSecurity.openNewOwnerOnlyProtocolFile(truncatedPath)) {
      channel.write(ByteBuffer.wrap(new byte[] {1}));
    }
    IOException truncated =
        assertThrows(
            IOException.class,
            () ->
                SqlitePosixCoordinationControlFileTransport.requireExistingExactRecord(
                    truncatedPath, magic));
    assertTrue(
        java.util.Objects.requireNonNull(truncated.getMessage(), "truncated-header message")
            .contains("unexpected size"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePosixCoordinationControlFileTransport.openOrCreateAndTryExclusiveLock(
                geometryPath, magic, -1L, 1L));
    try (SqliteCoordinationControlFiles.LockedControlFile afterFailure =
        java.util.Objects.requireNonNull(
            SqlitePosixCoordinationControlFileTransport.openExistingAndTryExclusiveLock(
                geometryPath, magic, SqliteCoordinationControlFiles.maintenanceLockPosition(), 1L),
            "lock after invalid geometry")) {
      assertTrue(Files.exists(geometryPath));
    }
  }

  @Test
  void physicalObjectCoordinationRejectsNonPosixFilesystemsRatherThanUsingOpaqueKeys()
      throws Exception {
    Path archive = tempDirectory.resolve("identity-without-posix.zip");
    try (FileSystem zipFileSystem =
        FileSystems.newFileSystem(URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
      Path artifact = Files.writeString(zipFileSystem.getPath("/book.sqlite"), "book");

      IOException failure =
          assertThrows(
              IOException.class,
              () -> SqlitePosixCoordinationFileSecurity.physicalObjectIdentity(artifact));

      assertTrue(
          java.util.Objects.requireNonNull(failure.getMessage(), "unsupported identity message")
              .contains("requires explicit POSIX device/inode identity"));
    }
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
