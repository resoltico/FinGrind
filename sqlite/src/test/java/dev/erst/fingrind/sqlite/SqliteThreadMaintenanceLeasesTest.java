package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Proves exact same-thread maintenance-lease ownership and fail-closed invariant checks. */
class SqliteThreadMaintenanceLeasesTest extends SqliteNativeBridgeTestSupport {

  @Test
  void directoryLease_retainsOnlyAdmittedArtifactsAndReleasesItsFinalReference() {
    Path directory = tempDirectory.resolve("directory");
    Path admitted = directory.resolve("admitted.sqlite");
    Path sibling = directory.resolve("sibling.sqlite");
    AtomicInteger handleCloses = new AtomicInteger();
    SqliteThreadMaintenanceLeases.DirectoryLease lease =
        directoryLease(directory, admitted, false, handleCloses);

    assertThrows(IllegalStateException.class, () -> lease.retain(sibling));
    SqliteThreadMaintenanceLeases.retainDirectoryLease(lease);
    SqliteHeldLease first = lease.retain(admitted);
    SqliteHeldLease second = lease.retain(admitted);
    assertTrue(lease.owns(admitted));

    first.close();
    assertTrue(lease.owns(admitted));
    assertEquals(0, handleCloses.get());
    second.close();

    assertNull(SqliteThreadMaintenanceLeases.directoryLease(directory));
    assertEquals(1, handleCloses.get());
  }

  @Test
  void directoryLease_failsClosedForMissingOwnershipOrRegistration() {
    Path directory = tempDirectory.resolve("invalid-directory");
    Path admitted = directory.resolve("admitted.sqlite");
    Path unowned = directory.resolve("unowned.sqlite");
    AtomicInteger handleCloses = new AtomicInteger();

    SqliteThreadMaintenanceLeases.DirectoryLease underReleased =
        directoryLease(directory, admitted, false, handleCloses);
    assertThrows(IllegalStateException.class, () -> underReleased.release(admitted));

    SqliteThreadMaintenanceLeases.DirectoryLease wrongArtifact =
        directoryLease(directory, admitted, false, handleCloses);
    SqliteThreadMaintenanceLeases.retainDirectoryLease(wrongArtifact);
    SqliteHeldLease held = wrongArtifact.retain(admitted);
    assertThrows(IllegalStateException.class, () -> wrongArtifact.release(unowned));
    held.close();

    SqliteThreadMaintenanceLeases.DirectoryLease unregistered =
        directoryLease(directory.resolve("unregistered"), admitted, false, handleCloses);
    SqliteHeldLease unregisteredHeld = unregistered.retain(admitted);
    assertThrows(IllegalStateException.class, unregisteredHeld::close);
    assertEquals(1, handleCloses.get());
  }

  @Test
  void directoryLease_admitsAnExplicitSiblingOnlyWhenItsPolicyAllowsIt() {
    Path directory = tempDirectory.resolve("sibling-directory");
    Path admitted = directory.resolve("admitted.sqlite");
    Path sibling = directory.resolve("sibling.sqlite");
    AtomicInteger handleCloses = new AtomicInteger();
    SqliteThreadMaintenanceLeases.DirectoryLease lease =
        directoryLease(directory, admitted, true, handleCloses);

    assertTrue(lease.permitsExplicitSiblingAdmission(sibling));
    lease.admitExplicitSibling(sibling);
    assertTrue(lease.admits(sibling));
    assertFalse(lease.permitsExplicitSiblingAdmission(sibling));
    assertThrows(IllegalStateException.class, () -> lease.admitExplicitSibling(sibling));
  }

  @Test
  void objectLease_releasesExactlyOnceAndRejectsMissingOwnership() {
    AtomicInteger handleCloses = new AtomicInteger();
    SqliteThreadMaintenanceLeases.ObjectLease underReleased =
        new SqliteThreadMaintenanceLeases.ObjectLease("under-released", leaseHandle(handleCloses));
    assertThrows(IllegalStateException.class, underReleased::release);

    SqliteThreadMaintenanceLeases.ObjectLease unregistered =
        new SqliteThreadMaintenanceLeases.ObjectLease("unregistered", leaseHandle(handleCloses));
    SqliteThreadMaintenanceLeases.ObjectLeaseReference unregisteredReference =
        unregistered.retain();
    assertThrows(IllegalStateException.class, unregisteredReference::release);

    SqliteThreadMaintenanceLeases.ObjectLease retained =
        new SqliteThreadMaintenanceLeases.ObjectLease("retained", leaseHandle(handleCloses));
    SqliteThreadMaintenanceLeases.retainObjectLease(retained);
    SqliteThreadMaintenanceLeases.ObjectLeaseReference reference =
        SqliteThreadMaintenanceLeases.retainCurrentThreadObjectLease("retained");
    assertEquals("retained", java.util.Objects.requireNonNull(reference).objectIdentity());
    reference.release();
    reference.release();

    assertNull(SqliteThreadMaintenanceLeases.objectLease("retained"));
    assertEquals(1, handleCloses.get());
  }

  private SqliteThreadMaintenanceLeases.DirectoryLease directoryLease(
      Path directory,
      Path admitted,
      boolean permitsExplicitSiblingAdmission,
      AtomicInteger handleCloses) {
    return new SqliteThreadMaintenanceLeases.DirectoryLease(
        directory,
        leaseHandle(handleCloses),
        Set.of(SqliteThreadMaintenanceLeases.DirectoryLease.artifactKey(admitted)),
        permitsExplicitSiblingAdmission);
  }

  private SqliteLeaseHandle leaseHandle(AtomicInteger closes) {
    Path controlPath = tempDirectory.resolve("control-" + closes.get() + ".control");
    return new SqliteLeaseHandle(
        controlPath,
        SqliteCoordinationControlFiles.lockedControlFile(
            controlPath,
            () -> {
              closes.incrementAndGet();
            }));
  }
}
