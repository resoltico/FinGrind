package dev.erst.fingrind.contract.bookkeeping;

import java.nio.file.Path;
import java.util.Objects;

/** Result family for exporting one closed encrypted-book backup pair. */
public sealed interface BackupBookResult
    permits BackupBookResult.BackedUp, BackupBookResult.Rejected {

  /** Successful encrypted-book backup outcome. */
  record BackedUp(Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath)
      implements BackupBookResult {
    public BackedUp {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      backupFilePath = normalizedPath(backupFilePath, "backupFilePath");
      backupBookKeyFilePath = normalizedPath(backupBookKeyFilePath, "backupBookKeyFilePath");
    }
  }

  /** Deterministic refusal for backup-book. */
  record Rejected(BookMaintenanceRejection rejection) implements BackupBookResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }

  private static Path normalizedPath(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }
}
