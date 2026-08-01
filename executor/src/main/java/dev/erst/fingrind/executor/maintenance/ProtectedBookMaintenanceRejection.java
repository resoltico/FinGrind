package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Local deterministic refusals for protected-book maintenance workflows. */
public sealed interface ProtectedBookMaintenanceRejection
    permits ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts,
        ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts,
        ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook,
        ProtectedBookMaintenanceRejection.PairTargetsConflict,
        ProtectedBookMaintenanceRejection.ArtifactPathInvalid,
        ProtectedBookMaintenanceRejection.ArtifactBusy,
        ProtectedBookMaintenanceRejection.BackupAcknowledgementConflict,
        ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists,
        ProtectedBookMaintenanceRejection.SecretTargetOccupied,
        ProtectedBookMaintenanceRejection.BookDestinationOccupied,
        ProtectedBookMaintenanceRejection.RecoveryPending,
        ProtectedBookMaintenanceRejection.ArtifactVerificationFailed {

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

  /** Rejection for restore commands whose backup source equals the selected live book path. */
  record BackupSourceMatchesLiveBook(Path bookFilePath, Path backupFilePath)
      implements ProtectedBookMaintenanceRejection {
    public BackupSourceMatchesLiveBook {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(backupFilePath, "backupFilePath");
    }
  }

  /**
   * Rejection for a pair request whose admitted targets resolve to one final filesystem identity.
   *
   * <p>The spellings intentionally remain distinct when an adapter established a case-folded or
   * otherwise filesystem-level alias.
   */
  record PairTargetsConflict(Path bookTargetPath, Path generatedSecretTargetPath)
      implements ProtectedBookMaintenanceRejection {
    public PairTargetsConflict {
      Objects.requireNonNull(bookTargetPath, "bookTargetPath");
      Objects.requireNonNull(generatedSecretTargetPath, "generatedSecretTargetPath");
    }
  }

  /** Rejection for maintenance commands whose selected artifact is actively in use. */
  record ArtifactPathInvalid(
      ProtectedBookMaintenanceArtifactRole artifactRole,
      Path artifactPath,
      ProtectedBookMaintenancePathFailure pathFailure)
      implements ProtectedBookMaintenanceRejection {
    public ArtifactPathInvalid {
      Objects.requireNonNull(artifactRole, "artifactRole");
      Objects.requireNonNull(artifactPath, "artifactPath");
      Objects.requireNonNull(pathFailure, "pathFailure");
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

  /** Rejection for a backup ID that is already bound to a different immutable acknowledgement. */
  record BackupAcknowledgementConflict(java.util.UUID backupId)
      implements ProtectedBookMaintenanceRejection {
    public BackupAcknowledgementConflict {
      Objects.requireNonNull(backupId, "backupId");
    }
  }

  /** Rejection for backup commands that refuse to overwrite one existing backup file. */
  record BackupDestinationAlreadyExists(Path backupFilePath)
      implements ProtectedBookMaintenanceRejection {
    public BackupDestinationAlreadyExists {
      Objects.requireNonNull(backupFilePath, "backupFilePath");
    }
  }

  /** Rejection for generated-secret workflows that refuse to overwrite an occupied target. */
  record SecretTargetOccupied(Path secretTargetPath) implements ProtectedBookMaintenanceRejection {
    public SecretTargetOccupied {
      Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    }
  }

  /** Rejection for restore commands lacking explicit consent to replace one existing book. */
  record BookDestinationOccupied(Path bookFilePath) implements ProtectedBookMaintenanceRejection {
    public BookDestinationOccupied {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
    }
  }

  /**
   * Rejection for a different request while a verified pair-publication recovery remains pending.
   */
  record RecoveryPending(
      OperationId recoveryOperation, Path bookTargetPath, Path generatedSecretTargetPath)
      implements ProtectedBookMaintenanceRejection {
    public RecoveryPending {
      Objects.requireNonNull(recoveryOperation, "recoveryOperation");
      Objects.requireNonNull(bookTargetPath, "bookTargetPath");
      Objects.requireNonNull(generatedSecretTargetPath, "generatedSecretTargetPath");
    }
  }

  /** Rejection for maintenance commands whose selected artifact failed verification. */
  record ArtifactVerificationFailed(
      ProtectedBookMaintenanceArtifactRole artifactRole,
      Path artifactPath,
      ProtectedBookVerificationFailure verificationFailure)
      implements ProtectedBookMaintenanceRejection {
    public ArtifactVerificationFailed {
      Objects.requireNonNull(artifactRole, "artifactRole");
      Objects.requireNonNull(artifactPath, "artifactPath");
      Objects.requireNonNull(verificationFailure, "verificationFailure");
    }
  }
}
