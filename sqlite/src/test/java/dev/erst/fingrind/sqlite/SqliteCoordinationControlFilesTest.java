package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Cross-platform contracts for retained owner-only coordination control files. */
class SqliteCoordinationControlFilesTest extends SqliteNativeBridgeTestSupport {
  @Test
  void controlLock_retainsTheExactControlAndExcludesAnOverlappingLocalClaim() throws Exception {
    hardenTestDirectory();
    Path controlPath = tempDirectory.resolve("coordination.control");
    byte[] magic = SqliteCoordinationControlProtocol.magic("test-control", "retained-control");

    try (SqliteCoordinationControlFiles.LockedControlFile ignored =
        requireLock(
            SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                controlPath,
                magic,
                SqliteCoordinationControlProtocol.maintenanceLockPosition(),
                1L))) {
      assertNull(
          SqliteCoordinationControlFiles.openExistingAndTryExclusiveLock(
              controlPath, magic, SqliteCoordinationControlProtocol.maintenanceLockPosition(), 1L));
      SqliteCoordinationControlFiles.requireExistingExactRecord(controlPath, magic);
      assertTrue(
          SqliteCoordinationControlFiles.physicalObjectIdentity(controlPath).contains("-v1:"));
    }

    try (SqliteCoordinationControlFiles.LockedControlFile ignored =
        requireLock(
            SqliteCoordinationControlFiles.openExistingAndTryExclusiveLock(
                controlPath,
                magic,
                SqliteCoordinationControlProtocol.maintenanceLockPosition(),
                1L))) {
      SqliteCoordinationControlFiles.requireExistingExactRecord(controlPath, magic);
    }
  }

  @Test
  void controlRecords_rejectChangedMagicAndDuplicateCreation() throws Exception {
    hardenTestDirectory();
    Path recordPath = tempDirectory.resolve("coordination.record");
    byte[] expected = SqliteCoordinationControlProtocol.magic("test-record", "expected");
    byte[] changed = SqliteCoordinationControlProtocol.magic("test-record", "changed!");

    SqliteCoordinationControlFiles.createAtomicallySecureRecord(recordPath, expected);
    SqliteCoordinationControlFiles.requireExistingExactRecord(recordPath, expected);

    IOException changedMagicFailure =
        assertThrows(
            IOException.class,
            () -> SqliteCoordinationControlFiles.requireExistingExactRecord(recordPath, changed));
    assertTrue(NullTestSupport.messageOf(changedMagicFailure).contains("magic is invalid"));
    assertThrows(
        FileAlreadyExistsException.class,
        () -> SqliteCoordinationControlFiles.createAtomicallySecureRecord(recordPath, expected));
  }

  @Test
  void controlOpening_rejectsTruncatedOrAlteredExistingContents() throws Exception {
    hardenTestDirectory();
    Path controlPath = tempDirectory.resolve("altered.control");
    byte[] magic = SqliteCoordinationControlProtocol.magic("test-control", "altered");
    try (PrivateOutputFile.OpenedFile opened = PrivateOutputFile.createNew(controlPath)) {
      opened.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
      opened.force();
    }

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                SqliteCoordinationControlFiles.openExistingAndTryExclusiveLock(
                    controlPath,
                    magic,
                    SqliteCoordinationControlProtocol.maintenanceLockPosition(),
                    1L));

    assertTrue(NullTestSupport.messageOf(failure).contains("unexpected size"));
  }

  @Test
  void controlLock_rejectsProtocolRangesBeforeOpeningTheArtifact() throws Exception {
    hardenTestDirectory();
    Path controlPath = tempDirectory.resolve("geometry.control");
    byte[] magic = SqliteCoordinationControlProtocol.magic("test-control", "geometry");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                controlPath, magic, SqliteCoordinationControlProtocol.CONTROL_LOCK_BASE - 1L, 1L));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                controlPath,
                magic,
                SqliteCoordinationControlProtocol.maintenanceLockPosition(),
                0L));
    assertFalse(Files.exists(controlPath, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void physicalObjectIdentity_matchesForAnAdmittedHardLink() throws Exception {
    hardenTestDirectory();
    Path original = tempDirectory.resolve("identity.control");
    Path alias = tempDirectory.resolve("identity-alias.control");
    byte[] magic = SqliteCoordinationControlProtocol.magic("test-control", "identity");
    SqliteCoordinationControlFiles.createAtomicallySecureRecord(original, magic);
    Files.createLink(alias, original);

    assertEquals(
        SqliteCoordinationControlFiles.physicalObjectIdentity(original),
        SqliteCoordinationControlFiles.physicalObjectIdentity(alias));
  }

  @Test
  void retainedControlClose_isIdempotentAndPreservesTheUnderlyingFailure() throws Exception {
    Path controlPath = tempDirectory.resolve("synthetic.control");
    IOException releaseFailure = new IOException("simulated release failure");
    java.util.concurrent.atomic.AtomicInteger closes =
        new java.util.concurrent.atomic.AtomicInteger();
    try (SqliteCoordinationControlFiles.LockedControlFile retained =
        SqliteCoordinationControlFiles.lockedControlFile(
            controlPath,
            () -> {
              closes.incrementAndGet();
              throw releaseFailure;
            })) {
      IOException failure = assertThrows(IOException.class, retained::close);

      assertSame(releaseFailure, failure.getCause());
      retained.close();
      assertEquals(1, closes.get());
    }
  }

  @Test
  void ownerOnlyAdmissionFailuresAreMappedAtEveryCoordinationControlBoundary() throws Exception {
    hardenTestDirectory();
    Path missingParentControl = tempDirectory.resolve("missing/control");
    byte[] magic = SqliteCoordinationControlProtocol.magic("test-control", "missing-parent");

    assertEquals(
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
        assertThrows(
                SqliteCallerPathContractException.class,
                () ->
                    SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                        missingParentControl,
                        magic,
                        SqliteCoordinationControlProtocol.maintenanceLockPosition(),
                        1L))
            .pathFailure());
    assertEquals(
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
        assertThrows(
                SqliteCallerPathContractException.class,
                () ->
                    SqliteCoordinationControlFiles.openExistingAndTryExclusiveLock(
                        missingParentControl,
                        magic,
                        SqliteCoordinationControlProtocol.maintenanceLockPosition(),
                        1L))
            .pathFailure());
    assertEquals(
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
        assertThrows(
                SqliteCallerPathContractException.class,
                () ->
                    SqliteCoordinationControlFiles.createAtomicallySecureRecord(
                        missingParentControl, magic))
            .pathFailure());
    assertEquals(
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
        assertThrows(
                SqliteCallerPathContractException.class,
                () -> SqliteCoordinationControlFiles.physicalObjectIdentity(missingParentControl))
            .pathFailure());

    Path controlPath = tempDirectory.resolve("wrong-magic.control");
    SqliteCoordinationControlFiles.createAtomicallySecureRecord(controlPath, magic);
    assertThrows(
        IOException.class,
        () ->
            SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                controlPath,
                SqliteCoordinationControlProtocol.magic("test-control", "wrong-magic"),
                SqliteCoordinationControlProtocol.maintenanceLockPosition(),
                1L));
  }

  @Test
  void retainedControlHelpersFailClosedAndPreserveEveryCleanupFailure() throws Exception {
    byte[] magic = SqliteCoordinationControlProtocol.magic("test-control", "helper-failures");

    try (ControlledOpenedFile stalledWrite =
            new ControlledOpenedFile(false, magic.length, -1, 0, null, null);
        ControlledOpenedFile endedRead =
            new ControlledOpenedFile(false, magic.length, -1, 1, null, null);
        ControlledOpenedFile stalledRead =
            new ControlledOpenedFile(false, magic.length, 0, 1, null, null);
        ControlledOpenedFile failedLock = new ControlledOpenedFile(false, 0L, -1, 1, null, null);
        ControlledOpenedFile closedAfterLockSuccess =
            new ControlledOpenedFile(false, 0L, -1, 1, null, null);
        ControlledOpenedFile closedAfterLockFailure =
            new ControlledOpenedFile(false, 0L, -1, 1, null, null)) {
      IOException stalledWriteFailure =
          assertThrows(
              IOException.class,
              () -> SqliteCoordinationControlFiles.writeExact(stalledWrite, magic));
      assertTrue(NullTestSupport.messageOf(stalledWriteFailure).contains("complete"));

      IOException endedReadFailure =
          assertThrows(
              IOException.class,
              () -> SqliteCoordinationControlFiles.requireExactMagic(endedRead, magic));
      assertTrue(NullTestSupport.messageOf(endedReadFailure).contains("ended unexpectedly"));

      IOException stalledReadFailure =
          assertThrows(
              IOException.class,
              () -> SqliteCoordinationControlFiles.requireExactMagic(stalledRead, magic));
      assertTrue(
          NullTestSupport.messageOf(stalledReadFailure).contains("did not make read progress"));

      IOException lockFailure = new IOException("lock failure");
      failedLock.failLockWith(lockFailure);
      assertSame(
          lockFailure,
          assertThrows(
              IOException.class,
              () ->
                  SqliteCoordinationControlFiles.tryLockAndRetain(
                      tempDirectory, failedLock, 0L, 1L)));
      assertEquals(1, failedLock.closeCount);

      IOException closeAfterLockSuccess = new IOException("close after lock success");
      closedAfterLockSuccess.failCloseWith(closeAfterLockSuccess);
      assertSame(
          closeAfterLockSuccess,
          assertThrows(
              IOException.class,
              () ->
                  SqliteCoordinationControlFiles.releaseLockAndFile(
                      () -> {}, closedAfterLockSuccess)));

      IOException lockReleaseFailure = new IOException("lock release failure");
      IOException closeAfterLockFailure = new IOException("close after lock failure");
      closedAfterLockFailure.failCloseWith(closeAfterLockFailure);
      IOException combinedFailure =
          assertThrows(
              IOException.class,
              () ->
                  SqliteCoordinationControlFiles.releaseLockAndFile(
                      () -> {
                        throw lockReleaseFailure;
                      },
                      closedAfterLockFailure));
      assertSame(lockReleaseFailure, combinedFailure);
      assertEquals(
          java.util.List.of(closeAfterLockFailure),
          java.util.List.of(combinedFailure.getSuppressed()));

      RuntimeException operationFailure = new RuntimeException("operation failure");
      IOException closeFailure = new IOException("close failure");
      SqliteCoordinationControlFiles.closePreserving(
          new ControlledOpenedFile(false, 0L, -1, 1, null, closeFailure), operationFailure);
      assertEquals(
          java.util.List.of(closeFailure), java.util.List.of(operationFailure.getSuppressed()));
    }
  }

  private SqliteCoordinationControlFiles.LockedControlFile requireLock(
      SqliteCoordinationControlFiles.@Nullable LockedControlFile lock) {
    assertNotNull(lock, "expected one available exact coordination lock");
    return java.util.Objects.requireNonNull(lock, "expected one available exact coordination lock");
  }

  private void hardenTestDirectory() throws IOException {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  /** Test double that records exact opened-file operations and deterministic cleanup failures. */
  private static final class ControlledOpenedFile implements PrivateOutputFile.OpenedFile {
    private final boolean created;
    private final long size;
    private final int readResult;
    private final int writeResult;
    private @Nullable IOException lockFailure;
    private @Nullable IOException closeFailure;
    private int closeCount;

    private ControlledOpenedFile(
        boolean created,
        long size,
        int readResult,
        int writeResult,
        @Nullable IOException lockFailure,
        @Nullable IOException closeFailure) {
      this.created = created;
      this.size = size;
      this.readResult = readResult;
      this.writeResult = writeResult;
      this.lockFailure = lockFailure;
      this.closeFailure = closeFailure;
    }

    private void failLockWith(IOException failure) {
      lockFailure = failure;
    }

    private void failCloseWith(IOException failure) {
      closeFailure = failure;
    }

    @Override
    public boolean created() {
      return created;
    }

    @Override
    public int read(ByteBuffer destination) {
      return readResult;
    }

    @Override
    public int write(ByteBuffer source) {
      return writeResult;
    }

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public long size() {
      return size;
    }

    @Override
    public void truncate(long newSize) {}

    @Override
    public void position(long position) {}

    @Override
    public void force() {}

    @Override
    public PrivateOutputFile.@Nullable HeldLock tryExclusiveLock(long position, long size)
        throws IOException {
      if (lockFailure != null) {
        throw lockFailure;
      }
      return () -> {};
    }

    @Override
    public String physicalObjectIdentity() {
      return "controlled";
    }

    @Override
    public void close() throws IOException {
      if (closeCount != 0) {
        return;
      }
      closeCount++;
      if (closeFailure != null) {
        throw closeFailure;
      }
    }
  }
}
