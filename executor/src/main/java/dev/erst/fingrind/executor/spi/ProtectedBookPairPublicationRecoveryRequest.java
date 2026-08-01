package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Exact caller input admitted to reconcile one completion-uncertain protected-book pair. */
public sealed interface ProtectedBookPairPublicationRecoveryRequest
    permits ProtectedBookPairPublicationRecoveryRequest.Backup,
        ProtectedBookPairPublicationRecoveryRequest.Restore,
        ProtectedBookPairPublicationRecoveryRequest.Rekey {

  /** Returns the lifecycle operation that owns the requested exact recovery. */
  default OperationId operation() {
    return switch (this) {
      case Backup _ -> OperationId.BACKUP_BOOK;
      case Restore _ -> OperationId.RESTORE_BOOK;
      case Rekey _ -> OperationId.REKEY_BOOK;
    };
  }

  /** Requests recovery of the exact caller-selected backup identity. */
  record Backup(Path sourceBookPath, UUID backupId)
      implements ProtectedBookPairPublicationRecoveryRequest {
    public Backup {
      sourceBookPath = normalized(sourceBookPath, "sourceBookPath");
      Objects.requireNonNull(backupId, "backupId");
    }
  }

  /** Requests recovery of the exact backup source verified for a restore operation. */
  record Restore(
      Path backupArtifactPath, Path backupKeyPath, AttestationBackupAcknowledgement acknowledgement)
      implements ProtectedBookPairPublicationRecoveryRequest {
    public Restore {
      backupArtifactPath = normalized(backupArtifactPath, "backupArtifactPath");
      backupKeyPath = normalized(backupKeyPath, "backupKeyPath");
      if (backupArtifactPath.equals(backupKeyPath)) {
        throw new IllegalArgumentException("Restore source artifact and key paths must differ.");
      }
      Objects.requireNonNull(acknowledgement, "acknowledgement");
    }
  }

  /** Requests recovery of the exact rekey source selected by the caller. */
  record Rekey(ProtectedBookPairPublicationSourceIdentity sourceIdentity)
      implements ProtectedBookPairPublicationRecoveryRequest {
    public Rekey {
      Objects.requireNonNull(sourceIdentity, "sourceIdentity");
    }
  }

  private static Path normalized(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }
}
