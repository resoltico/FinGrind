package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Behavioural coverage for the POSIX owner-only coordination-file transport. */
class SqlitePosixCoordinationControlFileTransportTest extends SqliteNativeBridgeTestSupport {
  @Test
  void platformTransportSelectionKeepsEachNativeBoundaryExplicit() {
    assertEquals(
        SqliteCoordinationControlFiles.CoordinationTransport.POSIX,
        SqliteCoordinationControlFiles.transportFor(false));
    assertEquals(
        SqliteCoordinationControlFiles.CoordinationTransport.WINDOWS,
        SqliteCoordinationControlFiles.transportFor(true));
  }

  @Test
  void windowsTransportNeverFallsBackToPosixOnANonWindowsHost() throws Exception {
    assumeFalse(SqliteCoordinationControlFiles.isWindows());
    Path controlPath = tempDirectory.resolve("windows-boundary.control");
    byte[] magic = SqliteCoordinationControlFiles.magic("test-control", "windows-boundary");
    SqliteCoordinationControlFiles.CoordinationTransport windows =
        SqliteCoordinationControlFiles.transportFor(true);

    assertWindowsNativeBoundary(() -> windows.openOrCreateAndTryExclusiveLock(controlPath, magic, 4_096L, 1L));
    assertWindowsNativeBoundary(() -> windows.openExistingAndTryExclusiveLock(controlPath, magic, 4_096L, 1L));
    assertWindowsNativeBoundary(() -> windows.createAtomicallySecureRecord(controlPath, magic));
    assertWindowsNativeBoundary(() -> windows.requireExistingExactRecord(controlPath, magic));
    assertWindowsNativeBoundary(() -> windows.physicalObjectIdentity(controlPath));
  }

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

  private static void assertWindowsNativeBoundary(ThrowingIoOperation operation) {
    IOException failure = assertThrows(IOException.class, operation::run);
    assertTrue(
        NullTestSupport.messageOf(failure).contains("could not load Windows native library"),
        "The selected Windows transport must reach its Win32 boundary rather than substitute POSIX.");
  }

  @FunctionalInterface
  private interface ThrowingIoOperation {
    void run() throws IOException;
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
  void transportPreservesCloseFailuresWhileReportingInitializationValidationAndLockFailures()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = ownerOnlyFixtureParent(fileSystem);
      byte[] magic = SqliteCoordinationControlFiles.magic("fixture", "close-failure");

      IOException writeFailure = new IOException("injected initialization write failure");
      IOException initializationCloseFailure =
          new IOException("injected initialization close failure");
      AclFixturePath initialization = fileSystem.path("\\controls\\initialization.control");
      initialization.failWriteWith(writeFailure).failCloseWith(initializationCloseFailure);
      IOException initializationFailure =
          assertThrows(
              IOException.class,
              () ->
                  SqlitePosixCoordinationControlFileTransport.openOrCreateAndTryExclusiveLock(
                      initialization, magic, 0L, 1L));
      assertEquals(writeFailure, initializationFailure);
      assertEquals(
          List.of(initializationCloseFailure), List.of(initializationFailure.getSuppressed()));

      IOException validationCloseFailure = new IOException("injected validation close failure");
      AclFixturePath invalidExisting = fileSystem.path("\\controls\\invalid-existing.control");
      invalidExisting.exists = true;
      invalidExisting.regularFile = true;
      invalidExisting.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      invalidExisting.replaceContent(new byte[magic.length]);
      invalidExisting.failCloseWith(validationCloseFailure);
      IOException validationFailure =
          assertThrows(
              IOException.class,
              () ->
                  SqlitePosixCoordinationControlFileTransport.openExistingAndTryExclusiveLock(
                      invalidExisting, magic, 0L, 1L));
      assertTrue(NullTestSupport.messageOf(validationFailure).contains("magic is invalid"));
      assertEquals(List.of(validationCloseFailure), List.of(validationFailure.getSuppressed()));

      IOException lockFailure = new IOException("injected lock failure");
      IOException lockCloseFailure = new IOException("injected lock close failure");
      AclFixturePath lockFailurePath = fileSystem.path("\\controls\\lock-failure.control");
      lockFailurePath.failTryLockWith(lockFailure).failCloseWith(lockCloseFailure);
      IOException retainedLockFailure =
          assertThrows(
              IOException.class,
              () ->
                  SqlitePosixCoordinationControlFileTransport.openOrCreateAndTryExclusiveLock(
                      lockFailurePath, magic, 0L, 1L));
      assertEquals(lockFailure, retainedLockFailure);
      assertEquals(List.of(lockCloseFailure), List.of(retainedLockFailure.getSuppressed()));

      AclFixturePath truncated = fileSystem.path("\\controls\\truncated-read.control");
      truncated.exists = true;
      truncated.regularFile = true;
      truncated.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      truncated.replaceContent(new byte[] {1});
      truncated.reportSizeAs(magic.length);
      IOException truncatedFailure =
          assertThrows(
              IOException.class,
              () ->
                  SqlitePosixCoordinationControlFileTransport.requireExistingExactRecord(
                      truncated, magic));
      assertTrue(NullTestSupport.messageOf(truncatedFailure).contains("ended unexpectedly"));

      assertTrue(parent.existsValue());
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
  void lockReleasePreservesFailuresFromBothTheLockAndItsRetainedChannel() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath controlPath = fileSystem.path("\\controls\\failing-release.control");
      controlPath.exists = true;
      controlPath.regularFile = true;
      IOException lockFailure = new IOException("injected lock release failure");
      IOException channelFailure = new IOException("injected channel close failure");
      FailingCloseFixtureFileChannel channel =
          new FailingCloseFixtureFileChannel(controlPath, channelFailure);

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  SqlitePosixCoordinationControlFileTransport.releaseLockAndChannel(
                      new FailingFixtureFileLock(channel, lockFailure), channel));

      assertEquals(lockFailure, failure);
      assertEquals(1, failure.getSuppressed().length);
      assertEquals(channelFailure, failure.getSuppressed()[0]);
    }
  }

  @Test
  void lockReleaseReportsAChannelFailureWhenTheLockItselfReleasesCleanly() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath controlPath = fileSystem.path("\\controls\\channel-only-release.control");
      controlPath.exists = true;
      controlPath.regularFile = true;
      IOException channelFailure = new IOException("injected channel close failure");
      FailingCloseFixtureFileChannel channel =
          new FailingCloseFixtureFileChannel(controlPath, channelFailure);

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  SqlitePosixCoordinationControlFileTransport.releaseLockAndChannel(
                      new FailingFixtureFileLock(channel, null), channel));

      assertEquals(channelFailure, failure);
      assertEquals(0, failure.getSuppressed().length);
    }
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

  private static AclFixturePath ownerOnlyFixtureParent(AclFixtureFileSystem fileSystem) {
    AclFixturePath parent = fileSystem.path("\\controls");
    parent.exists = true;
    parent.regularFile = false;
    parent.posixPermissions =
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    return parent;
  }

  /** Fixture descriptor that reports a close failure after the release boundary consumes it. */
  private static final class FailingCloseFixtureFileChannel extends AclFixtureFileChannel {
    private final IOException closeFailure;

    private FailingCloseFixtureFileChannel(AclFixturePath path, IOException closeFailure) {
      super(path, new AclFixtureSeekableByteChannel(path));
      this.closeFailure = closeFailure;
    }

    @Override
    protected void implCloseChannel() throws IOException {
      throw closeFailure;
    }
  }

  /** Fixture lock that may fail exactly once when the transport releases it. */
  private static final class FailingFixtureFileLock extends FileLock {
    private final @org.jspecify.annotations.Nullable IOException releaseFailure;
    private boolean valid = true;

    private FailingFixtureFileLock(
        FileChannel channel, @org.jspecify.annotations.Nullable IOException releaseFailure) {
      super(channel, 0L, 1L, false);
      this.releaseFailure = releaseFailure;
    }

    @Override
    public boolean isValid() {
      return valid;
    }

    @Override
    public void release() throws IOException {
      valid = false;
      if (releaseFailure != null) {
        throw releaseFailure;
      }
    }
  }
}
