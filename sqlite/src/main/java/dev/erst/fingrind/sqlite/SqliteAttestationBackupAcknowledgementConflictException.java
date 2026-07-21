package dev.erst.fingrind.sqlite;

import java.util.UUID;

/** Raised when a backup-created operation reuses an existing backup ID with a different tuple. */
final class SqliteAttestationBackupAcknowledgementConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final UUID backupId;

  SqliteAttestationBackupAcknowledgementConflictException(UUID backupId) {
    super("backup-acknowledgement-conflict");
    this.backupId = java.util.Objects.requireNonNull(backupId, "backupId");
  }

  UUID backupId() {
    return backupId;
  }
}
