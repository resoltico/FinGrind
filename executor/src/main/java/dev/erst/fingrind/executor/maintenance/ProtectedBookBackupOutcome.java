package dev.erst.fingrind.executor.maintenance;

import java.nio.file.Path;
import java.util.Objects;

/** Local result family for exporting one closed encrypted-book backup pair. */
public sealed interface ProtectedBookBackupOutcome
    permits ProtectedBookBackupOutcome.BackedUp, ProtectedBookBackupOutcome.Rejected {

  /** Successful encrypted-book backup outcome. */
  record BackedUp(Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath)
      implements ProtectedBookBackupOutcome {
    public BackedUp {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
    }
  }

  /** Deterministic refusal for backup-book. */
  record Rejected(ProtectedBookMaintenanceRejection rejection)
      implements ProtectedBookBackupOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
