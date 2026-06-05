package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.util.List;
import java.util.Objects;

/** Closed family of deterministic protected-book maintenance refusals. */
public sealed interface BookMaintenanceRejection
    permits BookMaintenanceRejection.BookHasBlockingArtifacts,
        BookMaintenanceRejection.BackupSourceHasBlockingArtifacts,
        BookMaintenanceRejection.BackupSourceMatchesLiveBook,
        BookMaintenanceRejection.ArtifactBusy,
        BookMaintenanceRejection.BackupDestinationAlreadyExists,
        BookMaintenanceRejection.BackupKeyFileAlreadyExists,
        BookMaintenanceRejection.ArtifactVerificationFailed,
        BookMaintenanceRejection.NoRollbackArtifactsFound,
        BookMaintenanceRejection.RollbackArtifactSelectionRequired,
        BookMaintenanceRejection.RollbackArtifactNotFound,
        BookMaintenanceRejection.RollbackArtifactNotForBook {

  /** Returns the stable wire code for one maintenance rejection instance. */
  static String wireCode(BookMaintenanceRejection rejection) {
    return BookMaintenanceRejectionDescriptors.wireCode(rejection);
  }

  /** Returns the canonical machine descriptors for every permitted maintenance rejection. */
  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return BookMaintenanceRejectionDescriptors.descriptors();
  }

  /** Rejection for maintenance commands that require a clean closed live book path. */
  record BookHasBlockingArtifacts(
      PublicPathHint bookFilePath, List<PublicPathHint> blockingArtifactPaths)
      implements BookMaintenanceRejection {
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
  record BackupSourceHasBlockingArtifacts(
      PublicPathHint backupFilePath, List<PublicPathHint> blockingArtifactPaths)
      implements BookMaintenanceRejection {
    public BackupSourceHasBlockingArtifacts {
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      blockingArtifactPaths =
          List.copyOf(Objects.requireNonNull(blockingArtifactPaths, "blockingArtifactPaths"));
      if (blockingArtifactPaths.isEmpty()) {
        throw new IllegalArgumentException("blockingArtifactPaths must not be empty.");
      }
    }
  }

  /** Rejection for restore commands whose selected backup source equals the live book path. */
  record BackupSourceMatchesLiveBook(PublicPathHint bookFilePath, PublicPathHint backupFilePath)
      implements BookMaintenanceRejection {
    public BackupSourceMatchesLiveBook {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(backupFilePath, "backupFilePath");
    }
  }

  /** Rejection for maintenance commands whose selected artifact is actively in use. */
  record ArtifactBusy(BookMaintenanceArtifactRole artifactRole, PublicPathHint artifactPath)
      implements BookMaintenanceRejection {
    public ArtifactBusy {
      Objects.requireNonNull(artifactRole, "artifactRole");
      Objects.requireNonNull(artifactPath, "artifactPath");
    }
  }

  /** Rejection for backup commands that refuse to overwrite an existing encrypted backup file. */
  record BackupDestinationAlreadyExists(PublicPathHint backupFilePath)
      implements BookMaintenanceRejection {
    public BackupDestinationAlreadyExists {
      Objects.requireNonNull(backupFilePath, "backupFilePath");
    }
  }

  /** Rejection for backup commands that refuse to overwrite an existing backup key file. */
  record BackupKeyFileAlreadyExists(PublicPathHint backupBookKeyFilePath)
      implements BookMaintenanceRejection {
    public BackupKeyFileAlreadyExists {
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
    }
  }

  /** Rejection for maintenance commands whose selected artifact failed verification. */
  record ArtifactVerificationFailed(
      BookMaintenanceArtifactRole artifactRole,
      PublicPathHint artifactPath,
      BookMaintenanceVerificationFailure verificationFailure)
      implements BookMaintenanceRejection {
    public ArtifactVerificationFailed {
      Objects.requireNonNull(artifactRole, "artifactRole");
      Objects.requireNonNull(artifactPath, "artifactPath");
      Objects.requireNonNull(verificationFailure, "verificationFailure");
    }
  }

  /** Rejection for rekey-recovery commands when no sibling rollback artifact exists. */
  record NoRollbackArtifactsFound(PublicPathHint bookFilePath) implements BookMaintenanceRejection {
    public NoRollbackArtifactsFound {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
    }
  }

  /** Rejection for rekey-recovery commands when more than one rollback artifact exists. */
  record RollbackArtifactSelectionRequired(
      PublicPathHint bookFilePath, List<PublicPathHint> rollbackArtifactPaths)
      implements BookMaintenanceRejection {
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

  /** Rejection for rekey-recovery commands that name a rollback artifact that is absent. */
  record RollbackArtifactNotFound(PublicPathHint rollbackArtifactPath)
      implements BookMaintenanceRejection {
    public RollbackArtifactNotFound {
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Rejection for rekey-recovery commands that name a non-sibling or non-canonical artifact. */
  record RollbackArtifactNotForBook(
      PublicPathHint bookFilePath, PublicPathHint rollbackArtifactPath)
      implements BookMaintenanceRejection {
    public RollbackArtifactNotForBook {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }
}
