package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.util.Objects;

/** Result family for exporting one closed encrypted-book backup pair. */
public sealed interface BackupBookResult
    permits BackupBookResult.BackedUp, BackupBookResult.Rejected {

  /** Successful encrypted-book backup outcome. */
  record BackedUp(
      PublicPathHint bookFilePath,
      PublicPathHint backupFilePath,
      PublicPathHint backupBookKeyFilePath)
      implements BackupBookResult {
    public BackedUp {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
    }
  }

  /** Deterministic refusal for backup-book. */
  record Rejected(BookMaintenanceRejection rejection) implements BackupBookResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
