package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceVerificationFailure;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Local deterministic refusals for protected-book maintenance workflows. */
public sealed interface ProtectedBookMaintenanceRejection
    permits ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts,
        ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts,
        ProtectedBookMaintenanceRejection.ArtifactBusy,
        ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists,
        ProtectedBookMaintenanceRejection.BackupKeyFileAlreadyExists,
        ProtectedBookMaintenanceRejection.ArtifactVerificationFailed,
        ProtectedBookMaintenanceRejection.NoRollbackArtifactsFound,
        ProtectedBookMaintenanceRejection.RollbackArtifactSelectionRequired,
        ProtectedBookMaintenanceRejection.RollbackArtifactNotFound,
        ProtectedBookMaintenanceRejection.RollbackArtifactNotForBook {

  /** Rejection for maintenance commands that require one clean closed live book path. */
  record BookHasBlockingArtifacts(Path bookFilePath, List<Path> blockingArtifactPaths)
      implements ProtectedBookMaintenanceRejection {
    public BookHasBlockingArtifacts {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      blockingArtifactPaths =
          List.copyOf(Objects.requireNonNull(blockingArtifactPaths, "blockingArtifactPaths"));
      if (blockingArtifactPaths.isEmpty()) {
        throw new IllegalArgumentException("blockingArtifactPaths must not be empty.");
      }
    }
  }

  /** Rejection for restore commands whose backup source carries unsafe SQLite sidecars. */
  record BackupSourceHasBlockingArtifacts(Path backupFilePath, List<Path> blockingArtifactPaths)
      implements ProtectedBookMaintenanceRejection {
    public BackupSourceHasBlockingArtifacts {
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      blockingArtifactPaths =
          List.copyOf(Objects.requireNonNull(blockingArtifactPaths, "blockingArtifactPaths"));
      if (blockingArtifactPaths.isEmpty()) {
        throw new IllegalArgumentException("blockingArtifactPaths must not be empty.");
      }
    }
  }

  /** Rejection for maintenance commands whose selected artifact is actively in use. */
  record ArtifactBusy(ProtectedBookMaintenanceArtifactRole artifactRole, Path artifactPath)
      implements ProtectedBookMaintenanceRejection {
    public ArtifactBusy {
      Objects.requireNonNull(artifactRole, "artifactRole");
      Objects.requireNonNull(artifactPath, "artifactPath");
    }
  }

  /** Rejection for backup commands that refuse to overwrite one existing backup file. */
  record BackupDestinationAlreadyExists(Path backupFilePath)
      implements ProtectedBookMaintenanceRejection {
    public BackupDestinationAlreadyExists {
      Objects.requireNonNull(backupFilePath, "backupFilePath");
    }
  }

  /** Rejection for backup commands that refuse to overwrite one existing backup key file. */
  record BackupKeyFileAlreadyExists(Path backupBookKeyFilePath)
      implements ProtectedBookMaintenanceRejection {
    public BackupKeyFileAlreadyExists {
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
    }
  }

  /** Rejection for maintenance commands whose selected artifact failed verification. */
  record ArtifactVerificationFailed(
      ProtectedBookMaintenanceArtifactRole artifactRole,
      Path artifactPath,
      ProtectedBookMaintenanceVerificationFailure verificationFailure)
      implements ProtectedBookMaintenanceRejection {
    public ArtifactVerificationFailed {
      Objects.requireNonNull(artifactRole, "artifactRole");
      Objects.requireNonNull(artifactPath, "artifactPath");
      Objects.requireNonNull(verificationFailure, "verificationFailure");
    }
  }

  /** Rejection for rekey-recovery commands when no sibling rollback artifact exists. */
  record NoRollbackArtifactsFound(Path bookFilePath) implements ProtectedBookMaintenanceRejection {
    public NoRollbackArtifactsFound {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
    }
  }

  /** Rejection for rekey-recovery commands when more than one rollback artifact exists. */
  record RollbackArtifactSelectionRequired(Path bookFilePath, List<Path> rollbackArtifactPaths)
      implements ProtectedBookMaintenanceRejection {
    public RollbackArtifactSelectionRequired {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      rollbackArtifactPaths =
          List.copyOf(Objects.requireNonNull(rollbackArtifactPaths, "rollbackArtifactPaths"));
      if (rollbackArtifactPaths.size() < 2) {
        throw new IllegalArgumentException(
            "rollbackArtifactPaths must contain at least two entries when selection is required.");
      }
    }
  }

  /** Rejection for rekey-recovery commands that name one absent rollback artifact. */
  record RollbackArtifactNotFound(Path rollbackArtifactPath)
      implements ProtectedBookMaintenanceRejection {
    public RollbackArtifactNotFound {
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Rejection for rekey-recovery commands that name one non-sibling rollback artifact. */
  record RollbackArtifactNotForBook(Path bookFilePath, Path rollbackArtifactPath)
      implements ProtectedBookMaintenanceRejection {
    public RollbackArtifactNotForBook {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }
}
