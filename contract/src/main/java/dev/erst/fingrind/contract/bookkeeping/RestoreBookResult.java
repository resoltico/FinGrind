package dev.erst.fingrind.contract.bookkeeping;

import java.nio.file.Path;
import java.util.Objects;

/** Result family for restoring one encrypted-book backup pair onto one live book path. */
public sealed interface RestoreBookResult
    permits RestoreBookResult.Restored, RestoreBookResult.Rejected {

  /** Successful restore outcome. */
  record Restored(Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath)
      implements RestoreBookResult {
    public Restored {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
    }
  }

  /** Deterministic refusal for restore-book. */
  record Rejected(BookMaintenanceRejection rejection) implements RestoreBookResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
