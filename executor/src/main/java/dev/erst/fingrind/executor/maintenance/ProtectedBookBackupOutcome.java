package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
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
      ProtectedBookPairPublicationCompletion pairPublicationCompletion,
      @org.jspecify.annotations.Nullable ProtectedBookPairPublicationRetention pairPublicationRetention,
      BackupAcknowledgementState acknowledgementState,
      @org.jspecify.annotations.Nullable AttestationCommit attestationCommit)
      implements ProtectedBookBackupOutcome {
    public BackedUp {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
      Objects.requireNonNull(backupId, "backupId");
      BackupAcknowledgementState checkedAcknowledgementState =
          Objects.requireNonNull(acknowledgementState, "acknowledgementState");
      pairPublicationCompletion =
          ProtectedBookPairPublicationCompletion.requireBackupCompletion(
              pairPublicationCompletion, checkedAcknowledgementState);
      pairPublicationRetention =
          ProtectedBookPairPublicationCompletion.requireRetention(
              pairPublicationCompletion, pairPublicationRetention);
      backupFilePath = authoritativePublishedBookPath(pairPublicationRetention, backupFilePath);
      backupBookKeyFilePath =
          authoritativePublishedGeneratedSecretPath(
              pairPublicationRetention, backupBookKeyFilePath);
      if (checkedAcknowledgementState == BackupAcknowledgementState.ACKNOWLEDGED
          && attestationCommit == null) {
        throw new IllegalArgumentException(
            "A newly acknowledged backup must report its attestation operation.");
      }
      if (checkedAcknowledgementState == BackupAcknowledgementState.ALREADY_PRESENT
          && attestationCommit != null) {
        throw new IllegalArgumentException(
            "An already-present backup acknowledgement must not report a newly appended"
                + " operation.");
      }
    }
  }

  /** Published backup whose durable source-book acknowledgement must be resumed explicitly. */
  record AcknowledgementPending(
      Path bookFilePath,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      java.util.UUID backupId,
      ProtectedBookPairPublicationCompletion pairPublicationCompletion,
      @org.jspecify.annotations.Nullable ProtectedBookPairPublicationRetention pairPublicationRetention)
      implements ProtectedBookBackupOutcome {
    public AcknowledgementPending {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
      Objects.requireNonNull(backupId, "backupId");
      Objects.requireNonNull(pairPublicationCompletion, "pairPublicationCompletion");
      pairPublicationRetention =
          ProtectedBookPairPublicationCompletion.requireRetention(
              pairPublicationCompletion, pairPublicationRetention);
      backupFilePath = authoritativePublishedBookPath(pairPublicationRetention, backupFilePath);
      backupBookKeyFilePath =
          authoritativePublishedGeneratedSecretPath(
              pairPublicationRetention, backupBookKeyFilePath);
    }
  }

  /** Published backup whose source-book acknowledgement was refused by current authorization. */
  record AcknowledgementAuthorizationRejected(
      Path bookFilePath,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      java.util.UUID backupId,
      ProtectedBookPairPublicationCompletion pairPublicationCompletion,
      @org.jspecify.annotations.Nullable ProtectedBookPairPublicationRetention pairPublicationRetention,
      AttestationAuthorizationFailure failure)
      implements ProtectedBookBackupOutcome {
    public AcknowledgementAuthorizationRejected {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
      Objects.requireNonNull(backupId, "backupId");
      Objects.requireNonNull(pairPublicationCompletion, "pairPublicationCompletion");
      pairPublicationRetention =
          ProtectedBookPairPublicationCompletion.requireRetention(
              pairPublicationCompletion, pairPublicationRetention);
      backupFilePath = authoritativePublishedBookPath(pairPublicationRetention, backupFilePath);
      backupBookKeyFilePath =
          authoritativePublishedGeneratedSecretPath(
              pairPublicationRetention, backupBookKeyFilePath);
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

  private static Path authoritativePublishedBookPath(
      @org.jspecify.annotations.Nullable ProtectedBookPairPublicationRetention pairPublicationRetention,
      Path backupFilePath) {
    if (pairPublicationRetention == null) {
      return backupFilePath;
    }
    return pairPublicationRetention.requireBookPublication(backupFilePath).publishedArtifactPath();
  }

  private static Path authoritativePublishedGeneratedSecretPath(
      @org.jspecify.annotations.Nullable ProtectedBookPairPublicationRetention pairPublicationRetention,
      Path backupBookKeyFilePath) {
    if (pairPublicationRetention == null) {
      return backupBookKeyFilePath;
    }
    return pairPublicationRetention
        .requireGeneratedSecretPublication(backupBookKeyFilePath)
        .publishedArtifactPath();
  }
}
