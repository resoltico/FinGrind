package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;

/** Protected-book maintenance rejection details emitted by the CLI transport layer. */
public interface CliMaintenanceRejectionJsonModels {
  /** Sealed category for protected-book maintenance rejection payloads. */
  sealed interface MaintenanceRejectionDetails extends CliRejectionJsonModels.RejectionDetails
      permits BookFileDetails,
          BookAndBackupFileDetails,
          BlockingArtifactsDetails,
          CliArtifactPathFailureDetails,
          ArtifactBusyDetails,
          BackupAcknowledgementConflictDetails,
          ArtifactVerificationFailureDetails,
          BackupFileDetails,
          PairTargetsConflictDetails,
          RecoveryPendingDetails,
          SecretTargetDetails {}

  /** Details for an immutable backup acknowledgement identity conflict. */
  record BackupAcknowledgementConflictDetails(String backupId)
      implements MaintenanceRejectionDetails {
    public BackupAcknowledgementConflictDetails {
      backupId = requireText(backupId, "backupId");
    }
  }

  record BookFileDetails(String bookFile) implements MaintenanceRejectionDetails {
    public BookFileDetails {
      bookFile = requireText(bookFile, "bookFile");
    }
  }

  record BookAndBackupFileDetails(String bookFile, String backupFile)
      implements MaintenanceRejectionDetails {
    public BookAndBackupFileDetails {
      bookFile = requireText(bookFile, "bookFile");
      backupFile = requireText(backupFile, "backupFile");
    }
  }

  record BlockingArtifactsDetails(String bookFile, List<String> blockingArtifacts)
      implements MaintenanceRejectionDetails {
    public BlockingArtifactsDetails {
      bookFile = requireText(bookFile, "bookFile");
      blockingArtifacts = copyList(blockingArtifacts, "blockingArtifacts");
      if (blockingArtifacts.isEmpty()) {
        throw new IllegalArgumentException("blockingArtifacts must not be empty.");
      }
    }
  }

  record BackupFileDetails(String backupFile) implements MaintenanceRejectionDetails {
    public BackupFileDetails {
      backupFile = requireText(backupFile, "backupFile");
    }
  }

  record SecretTargetDetails(String secretTarget) implements MaintenanceRejectionDetails {
    public SecretTargetDetails {
      secretTarget = requireText(secretTarget, "secretTarget");
    }
  }

  /** Details for a rejected request whose two final pair members resolve to one identity. */
  record PairTargetsConflictDetails(String bookTarget, String generatedSecretTarget)
      implements MaintenanceRejectionDetails {
    public PairTargetsConflictDetails {
      bookTarget = requireText(bookTarget, "bookTarget");
      generatedSecretTarget = requireText(generatedSecretTarget, "generatedSecretTarget");
    }
  }

  /** Details for a verified protected-book pair publication that must be resumed exactly. */
  record RecoveryPendingDetails(
      String recoveryOperation, String bookTarget, String generatedSecretTarget)
      implements MaintenanceRejectionDetails {
    public RecoveryPendingDetails {
      recoveryOperation = requireText(recoveryOperation, "recoveryOperation");
      bookTarget = requireText(bookTarget, "bookTarget");
      generatedSecretTarget = requireText(generatedSecretTarget, "generatedSecretTarget");
    }
  }

  record ArtifactBusyDetails(String artifactRole, String artifactPath)
      implements MaintenanceRejectionDetails {
    public ArtifactBusyDetails {
      artifactRole = requireText(artifactRole, "artifactRole");
      artifactPath = requireText(artifactPath, "artifactPath");
    }
  }

  record ArtifactVerificationFailureDetails(
      String artifactRole, String artifactPath, String verificationFailure)
      implements MaintenanceRejectionDetails {
    public ArtifactVerificationFailureDetails {
      artifactRole = requireText(artifactRole, "artifactRole");
      artifactPath = requireText(artifactPath, "artifactPath");
      verificationFailure = requireText(verificationFailure, "verificationFailure");
    }
  }
}
