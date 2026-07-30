package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Closed family of deterministic protected-book maintenance refusals. */
public sealed interface BookMaintenanceRejection
    permits BookMaintenanceRejection.MaintenanceStateConflict,
        BookMaintenanceRejection.MaintenanceArtifactInvalid,
        BookMaintenanceRejection.MaintenanceRequestInvalid {

  /** Returns the stable wire code for one maintenance rejection instance. */
  static String wireCode(BookMaintenanceRejection rejection) {
    return BookMaintenanceRejectionDescriptors.wireCode(rejection);
  }

  /** Returns the canonical process exit code for one maintenance rejection. */
  static int exitCode(BookMaintenanceRejection rejection) {
    return BookMaintenanceRejectionDescriptors.exitCode(rejection);
  }

  /** Returns the canonical machine descriptors for every permitted maintenance rejection. */
  static List<RejectionDescriptor> descriptors() {
    return BookMaintenanceRejectionDescriptors.descriptors();
  }

  /** Closed subfamily of refusals caused by occupied or unsafe maintenance state. */
  sealed interface MaintenanceStateConflict extends BookMaintenanceRejection
      permits BookHasBlockingArtifacts,
          BackupSourceHasBlockingArtifacts,
          ArtifactBusy,
          BackupAcknowledgementConflict,
          BackupDestinationAlreadyExists,
          SecretTargetOccupied,
          BookDestinationOccupied,
          RecoveryPending {}

  /** Closed subfamily of refusals caused by an invalid protected-book artifact. */
  sealed interface MaintenanceArtifactInvalid extends BookMaintenanceRejection
      permits ArtifactPathInvalid, ArtifactVerificationFailed {}

  /** Closed subfamily of refusals caused by an invalid maintenance request. */
  sealed interface MaintenanceRequestInvalid extends BookMaintenanceRejection
      permits BackupSourceMatchesLiveBook, PairTargetsConflict {}

  /** Rejection for maintenance commands that require a clean closed live book path. */
  record BookHasBlockingArtifacts(Path bookFilePath, List<Path> blockingArtifactPaths)
      implements MaintenanceStateConflict {
    public BookHasBlockingArtifacts {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      blockingArtifactPaths = normalizedPaths(blockingArtifactPaths, "blockingArtifactPaths");
      if (blockingArtifactPaths.isEmpty()) {
        throw new IllegalArgumentException("blockingArtifactPaths must not be empty.");
      }
    }
  }

  /** Rejection for restore commands whose backup source carries unsafe SQLite sidecars. */
  record BackupSourceHasBlockingArtifacts(Path backupFilePath, List<Path> blockingArtifactPaths)
      implements MaintenanceStateConflict {
    public BackupSourceHasBlockingArtifacts {
      backupFilePath = normalizedPath(backupFilePath, "backupFilePath");
      blockingArtifactPaths = normalizedPaths(blockingArtifactPaths, "blockingArtifactPaths");
      if (blockingArtifactPaths.isEmpty()) {
        throw new IllegalArgumentException("blockingArtifactPaths must not be empty.");
      }
    }
  }

  /** Rejection for restore commands whose selected backup source equals the live book path. */
  record BackupSourceMatchesLiveBook(Path bookFilePath, Path backupFilePath)
      implements MaintenanceRequestInvalid {
    public BackupSourceMatchesLiveBook {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      backupFilePath = normalizedPath(backupFilePath, "backupFilePath");
    }
  }

  /**
   * Rejection for a pair request whose admitted targets resolve to one final filesystem identity.
   *
   * <p>Both submitted spellings remain observable because a case-folded alias need not be lexically
   * equal.
   */
  record PairTargetsConflict(Path bookTarget, Path generatedSecretTarget)
      implements MaintenanceRequestInvalid {
    public PairTargetsConflict {
      bookTarget = normalizedPath(bookTarget, "bookTarget");
      generatedSecretTarget = normalizedPath(generatedSecretTarget, "generatedSecretTarget");
    }
  }

  /** Rejection for maintenance commands whose selected artifact is actively in use. */
  record ArtifactPathInvalid(
      BookMaintenanceArtifactRole artifactRole,
      Path artifactPath,
      BookMaintenancePathFailure pathFailure)
      implements MaintenanceArtifactInvalid {
    public ArtifactPathInvalid {
      Objects.requireNonNull(artifactRole, "artifactRole");
      artifactPath = normalizedPath(artifactPath, "artifactPath");
      Objects.requireNonNull(pathFailure, "pathFailure");
    }
  }

  /** Rejection for maintenance commands whose selected artifact is actively in use. */
  record ArtifactBusy(BookMaintenanceArtifactRole artifactRole, Path artifactPath)
      implements MaintenanceStateConflict {
    public ArtifactBusy {
      Objects.requireNonNull(artifactRole, "artifactRole");
      artifactPath = normalizedPath(artifactPath, "artifactPath");
    }
  }

  /** Rejection for a backup ID that is already bound to another immutable acknowledgement. */
  record BackupAcknowledgementConflict(java.util.UUID backupId)
      implements MaintenanceStateConflict {
    public BackupAcknowledgementConflict {
      Objects.requireNonNull(backupId, "backupId");
    }
  }

  /** Rejection for backup commands that refuse to overwrite an existing encrypted backup file. */
  record BackupDestinationAlreadyExists(Path backupFilePath) implements MaintenanceStateConflict {
    public BackupDestinationAlreadyExists {
      backupFilePath = normalizedPath(backupFilePath, "backupFilePath");
    }
  }

  /** Rejection for generated-secret workflows that refuse to overwrite an occupied target. */
  record SecretTargetOccupied(Path secretTargetPath) implements MaintenanceStateConflict {
    public SecretTargetOccupied {
      secretTargetPath = normalizedPath(secretTargetPath, "secretTargetPath");
    }
  }

  /** Rejection for restore commands whose destination book path is already occupied. */
  record BookDestinationOccupied(Path bookFilePath) implements MaintenanceStateConflict {
    public BookDestinationOccupied {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
    }
  }

  /**
   * Rejection for a different request while one verified pair-publication recovery remains pending.
   */
  record RecoveryPending(
      OperationId recoveryOperation, Path bookTargetPath, Path generatedSecretTargetPath)
      implements MaintenanceStateConflict {
    public RecoveryPending {
      Objects.requireNonNull(recoveryOperation, "recoveryOperation");
      bookTargetPath = normalizedPath(bookTargetPath, "bookTargetPath");
      generatedSecretTargetPath =
          normalizedPath(generatedSecretTargetPath, "generatedSecretTargetPath");
    }
  }

  /** Rejection for maintenance commands whose selected artifact failed verification. */
  record ArtifactVerificationFailed(
      BookMaintenanceArtifactRole artifactRole,
      Path artifactPath,
      BookMaintenanceVerificationFailure verificationFailure)
      implements MaintenanceArtifactInvalid {
    public ArtifactVerificationFailed {
      Objects.requireNonNull(artifactRole, "artifactRole");
      artifactPath = normalizedPath(artifactPath, "artifactPath");
      Objects.requireNonNull(verificationFailure, "verificationFailure");
    }
  }

  private static Path normalizedPath(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }

  private static List<Path> normalizedPaths(List<Path> paths, String name) {
    return List.copyOf(Objects.requireNonNull(paths, name)).stream()
        .map(path -> normalizedPath(path, name + " entry"))
        .toList();
  }
}
