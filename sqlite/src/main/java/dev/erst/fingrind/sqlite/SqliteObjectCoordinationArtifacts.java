package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Retained per-user O(1) coordination controls keyed by explicit physical-object identity.
 *
 * <p>A v4 object name is the SHA-256 of an explicit POSIX device/inode tuple or an explicit Windows
 * volume/file-id tuple. It is not a path spelling, a registry record, or an opaque provider {@code
 * fileKey} rendering. The immutable header binds the retained control to that same identity before
 * a lock is acquired.
 */
final class SqliteObjectCoordinationArtifacts {
  private static final String OBJECT_PROTOCOL = "FinGrind-object-coordination-v4";
  static final int ACTIVITY_SLOT_COUNT = 1_024;

  private SqliteObjectCoordinationArtifacts() {}

  /** Resolves one existing regular artifact into its immutable v4 O(1) control domain. */
  static Domain domainForExistingArtifact(Path existingArtifactPath) throws IOException {
    String objectIdentity = physicalIdentity(existingArtifactPath);
    Path controlPath =
        SqliteObjectCoordinationRoot.objectControlPath(
            SqliteObjectCoordinationRoot.requirePrivateRoot(), objectIdentity);
    return new Domain(
        objectIdentity,
        controlPath,
        SqliteCoordinationControlProtocol.magic(OBJECT_PROTOCOL, objectIdentity));
  }

  /** Acquires the sole maintenance exclusion for one physical artifact, or reports it busy. */
  static @Nullable SqliteLeaseHandle tryAcquireMaintenanceExclusion(Path existingArtifactPath)
      throws IOException {
    return tryAcquireMaintenanceExclusion(domainForExistingArtifact(existingArtifactPath));
  }

  /** Acquires the sole maintenance exclusion for one already-resolved object-control domain. */
  static @Nullable SqliteLeaseHandle tryAcquireMaintenanceExclusion(Domain domain)
      throws IOException {
    Domain checkedDomain = Objects.requireNonNull(domain, "domain");
    return retainedLease(
        checkedDomain.controlPath(),
        SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
            checkedDomain.controlPath(),
            checkedDomain.magic(),
            SqliteCoordinationControlProtocol.maintenanceLockPosition(),
            SqliteCoordinationControlProtocol.maintenanceLockLength()));
  }

  /** Acquires one activity slot for a physical artifact, or fails only after every slot is held. */
  static ActivitySlot acquireActivitySlot(Domain domain) throws IOException {
    Domain checkedDomain = Objects.requireNonNull(domain, "domain");
    for (int slot = 0; slot < ACTIVITY_SLOT_COUNT; slot++) {
      @Nullable ActivitySlot slotHandle =
          activitySlot(
              SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                  checkedDomain.controlPath(),
                  checkedDomain.magic(),
                  SqliteCoordinationControlProtocol.activitySlotPosition(slot),
                  1L));
      if (slotHandle != null) {
        return slotHandle;
      }
    }
    throw new IOException("No FinGrind object-coordination activity slot is available.");
  }

  /** Returns whether any activity slot for the physical artifact is presently held. */
  static boolean hasActiveSlot(Path existingArtifactPath) throws IOException {
    Domain domain = domainForExistingArtifact(existingArtifactPath);
    if (Files.notExists(domain.controlPath(), LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    for (int slot = 0; slot < ACTIVITY_SLOT_COUNT; slot++) {
      if (isActivitySlotHeld(
          SqliteCoordinationControlFiles.openExistingAndTryExclusiveLock(
              domain.controlPath(),
              domain.magic(),
              SqliteCoordinationControlProtocol.activitySlotPosition(slot),
              1L))) {
        return true;
      }
    }
    return false;
  }

  /** Returns the explicit physical identity without creating a coordination control. */
  static String physicalIdentity(Path existingArtifactPath) throws IOException {
    return SqliteCoordinationControlFiles.physicalObjectIdentity(existingArtifactPath);
  }

  /** Reports a held slot, or releases a successful availability probe before continuing. */
  private static boolean isActivitySlotHeld(
      SqliteCoordinationControlFiles.@Nullable LockedControlFile probe) throws IOException {
    if (probe == null) {
      return true;
    }
    probe.close();
    return false;
  }

  private static @Nullable SqliteLeaseHandle retainedLease(
      Path controlPath, SqliteCoordinationControlFiles.@Nullable LockedControlFile lockedControl) {
    return lockedControl == null ? null : new SqliteLeaseHandle(controlPath, lockedControl);
  }

  private static @Nullable ActivitySlot activitySlot(
      SqliteCoordinationControlFiles.@Nullable LockedControlFile lockedControl) {
    return lockedControl == null ? null : new ActivitySlot(lockedControl);
  }

  /** Immutable v4 control domain for one explicit physical artifact identity. */
  static final class Domain {
    private final String objectIdentity;
    private final Path controlPath;
    private final byte[] magic;

    Domain(String objectIdentity, Path controlPath, byte[] magic) {
      this.objectIdentity = Objects.requireNonNull(objectIdentity, "objectIdentity");
      this.controlPath = Objects.requireNonNull(controlPath, "controlPath");
      this.magic = Objects.requireNonNull(magic, "magic").clone();
    }

    String objectIdentity() {
      return objectIdentity;
    }

    Path controlPath() {
      return controlPath;
    }

    byte[] magic() {
      return magic.clone();
    }
  }

  /** Opaque held object-coordination activity slot. */
  record ActivitySlot(SqliteCoordinationControlFiles.LockedControlFile control)
      implements AutoCloseable {
    ActivitySlot {
      Objects.requireNonNull(control, "control");
    }

    @Override
    public void close() throws IOException {
      control.close();
    }
  }
}
