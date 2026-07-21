package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.BackupAcknowledgementConflictException;
import java.util.UUID;

/** Raised when a backup-created operation reuses an existing backup ID with a different tuple. */
final class SqliteAttestationBackupAcknowledgementConflictException
    extends BackupAcknowledgementConflictException {
  private static final long serialVersionUID = 1L;

  SqliteAttestationBackupAcknowledgementConflictException(UUID backupId) {
    super(backupId);
  }
}
