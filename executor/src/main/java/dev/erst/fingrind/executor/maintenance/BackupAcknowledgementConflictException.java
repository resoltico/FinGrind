package dev.erst.fingrind.executor.maintenance;

import java.util.Objects;
import java.util.UUID;

/** Raised at the attestation admission boundary for conflicting reuse of one backup identity. */
public class BackupAcknowledgementConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final UUID backupId;

  /** Captures the reused identity without retaining the conflicting artifact tuple. */
  public BackupAcknowledgementConflictException(UUID backupId) {
    super("backup-acknowledgement-conflict");
    this.backupId = Objects.requireNonNull(backupId, "backupId");
  }

  /** Returns the backup identity that was already acknowledged differently. */
  public UUID backupId() {
    return backupId;
  }
}
