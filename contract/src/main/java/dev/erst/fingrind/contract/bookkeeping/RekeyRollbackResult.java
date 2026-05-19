package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.util.List;
import java.util.Objects;

/** Result family for the split rekey-rollback maintenance commands. */
public sealed interface RekeyRollbackResult
    permits RekeyRollbackResult.Inspected,
        RekeyRollbackResult.Restored,
        RekeyRollbackResult.Deleted,
        RekeyRollbackResult.Rejected {

  /** Successful inspection outcome listing every matching sibling rollback artifact. */
  record Inspected(PublicPathHint bookFilePath, List<PublicPathHint> rollbackArtifactPaths)
      implements RekeyRollbackResult {
    public Inspected {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      rollbackArtifactPaths =
          List.copyOf(Objects.requireNonNull(rollbackArtifactPaths, "rollbackArtifactPaths"));
    }
  }

  /** Successful rollback-copy restore outcome. */
  record Restored(PublicPathHint bookFilePath, PublicPathHint rollbackArtifactPath)
      implements RekeyRollbackResult {
    public Restored {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Successful rollback-copy deletion outcome. */
  record Deleted(PublicPathHint bookFilePath, PublicPathHint rollbackArtifactPath)
      implements RekeyRollbackResult {
    public Deleted {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Deterministic refusal for one rekey-rollback maintenance command. */
  record Rejected(BookMaintenanceRejection rejection) implements RekeyRollbackResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
