package dev.erst.fingrind.executor.maintenance;

import java.nio.file.Path;
import java.util.Objects;

/** Local result family for restoring one encrypted-book backup pair. */
public sealed interface ProtectedBookRestoreOutcome
    permits ProtectedBookRestoreOutcome.Restored, ProtectedBookRestoreOutcome.Rejected {

  /** Successful restore outcome. */
  record Restored(Path bookFilePath, Path bookKeyFilePath) implements ProtectedBookRestoreOutcome {
    public Restored {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    }
  }

  /** Deterministic refusal for restore-book. */
  record Rejected(ProtectedBookMaintenanceRejection rejection)
      implements ProtectedBookRestoreOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
