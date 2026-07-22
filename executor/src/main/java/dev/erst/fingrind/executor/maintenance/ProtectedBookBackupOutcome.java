package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import java.nio.file.Path;
import java.util.Objects;

/** Local result family for exporting one closed encrypted-book backup pair. */
public sealed interface ProtectedBookBackupOutcome
    permits ProtectedBookBackupOutcome.BackedUp,
        ProtectedBookBackupOutcome.AcknowledgementPending,
        ProtectedBookBackupOutcome.AcknowledgementAuthorizationRejected,
        ProtectedBookBackupOutcome.Rejected {

  /** Successful encrypted-book backup outcome. */
  record BackedUp(
      Path bookFilePath,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      java.util.UUID backupId,
      boolean acknowledgementResumed)
      implements ProtectedBookBackupOutcome {
    public BackedUp {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
      Objects.requireNonNull(backupId, "backupId");
    }
  }

  /** Published backup whose durable source-book acknowledgement must be resumed explicitly. */
  record AcknowledgementPending(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath, java.util.UUID backupId)
      implements ProtectedBookBackupOutcome {
    public AcknowledgementPending {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
      Objects.requireNonNull(backupId, "backupId");
    }
  }

  /** Published backup whose source-book acknowledgement was refused by current authorization. */
  record AcknowledgementAuthorizationRejected(
      Path bookFilePath,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      java.util.UUID backupId,
      AttestationAuthorizationFailure failure)
      implements ProtectedBookBackupOutcome {
    public AcknowledgementAuthorizationRejected {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
      Objects.requireNonNull(backupId, "backupId");
      Objects.requireNonNull(failure, "failure");
    }
  }

  /** Deterministic refusal for backup-book. */
  record Rejected(ProtectedBookMaintenanceRejection rejection)
      implements ProtectedBookBackupOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
