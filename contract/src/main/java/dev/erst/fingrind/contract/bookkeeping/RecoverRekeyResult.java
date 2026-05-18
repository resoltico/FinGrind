package dev.erst.fingrind.contract.bookkeeping;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Result family for inspecting or acting on stale sibling rekey rollback artifacts. */
public sealed interface RecoverRekeyResult
    permits RecoverRekeyResult.Inspected,
        RecoverRekeyResult.Restored,
        RecoverRekeyResult.Deleted,
        RecoverRekeyResult.Rejected {

  /** Successful inspection outcome listing every matching sibling rollback artifact. */
  record Inspected(Path bookFilePath, List<Path> rollbackArtifactPaths)
      implements RecoverRekeyResult {
    public Inspected {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      rollbackArtifactPaths =
          List.copyOf(Objects.requireNonNull(rollbackArtifactPaths, "rollbackArtifactPaths"));
    }
  }

  /** Successful rollback-copy restore outcome. */
  record Restored(Path bookFilePath, Path rollbackArtifactPath) implements RecoverRekeyResult {
    public Restored {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Successful rollback-copy deletion outcome. */
  record Deleted(Path bookFilePath, Path rollbackArtifactPath) implements RecoverRekeyResult {
    public Deleted {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Deterministic refusal for recover-rekey. */
  record Rejected(BookMaintenanceRejection rejection) implements RecoverRekeyResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
