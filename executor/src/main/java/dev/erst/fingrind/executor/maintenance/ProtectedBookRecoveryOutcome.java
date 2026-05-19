package dev.erst.fingrind.executor.maintenance;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Local result family for the split rekey-rollback maintenance commands. */
public sealed interface ProtectedBookRecoveryOutcome
    permits ProtectedBookRecoveryOutcome.Inspected,
        ProtectedBookRecoveryOutcome.Restored,
        ProtectedBookRecoveryOutcome.Deleted,
        ProtectedBookRecoveryOutcome.Rejected {

  /** Successful inspection outcome listing every matching sibling rollback artifact. */
  record Inspected(Path bookFilePath, List<Path> rollbackArtifactPaths)
      implements ProtectedBookRecoveryOutcome {
    public Inspected {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      rollbackArtifactPaths =
          List.copyOf(Objects.requireNonNull(rollbackArtifactPaths, "rollbackArtifactPaths"));
    }
  }

  /** Successful rollback-copy restore outcome. */
  record Restored(Path bookFilePath, Path rollbackArtifactPath)
      implements ProtectedBookRecoveryOutcome {
    public Restored {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Successful rollback-copy deletion outcome. */
  record Deleted(Path bookFilePath, Path rollbackArtifactPath)
      implements ProtectedBookRecoveryOutcome {
    public Deleted {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Deterministic refusal for one rekey-rollback maintenance command. */
  record Rejected(ProtectedBookMaintenanceRejection rejection)
      implements ProtectedBookRecoveryOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
