package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.util.Objects;

/** Result family for restoring one encrypted-book backup pair onto one live book path. */
public sealed interface RestoreBookResult
    permits RestoreBookResult.Restored, RestoreBookResult.Rejected {

  /** Successful restore outcome. */
  record Restored(PublicPathHint bookFilePath, PublicPathHint bookKeyFilePath)
      implements RestoreBookResult {
    public Restored {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    }
  }

  /** Deterministic refusal for restore-book. */
  record Rejected(BookMaintenanceRejection rejection) implements RestoreBookResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
