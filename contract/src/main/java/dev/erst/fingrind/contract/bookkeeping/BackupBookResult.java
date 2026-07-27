package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.AdmissionContext;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Result family for exporting one closed encrypted-book backup pair. */
public sealed interface BackupBookResult
    permits BackupBookResult.BackedUp,
        BackupBookResult.AcknowledgementPending,
        BackupBookResult.AcknowledgementAuthorizationRejected,
        BackupBookResult.Rejected {

  /** Successful encrypted-book backup outcome. */
  record BackedUp(
      Path bookFilePath,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      java.util.UUID backupId,
      ProtectedBookPairPublicationCompletion pairPublicationCompletion,
      @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention,
      BackupAcknowledgementState acknowledgementState,
      @Nullable AttestationCommit attestationCommit)
      implements BackupBookResult {
    public BackedUp {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      backupFilePath = normalizedPath(backupFilePath, "backupFilePath");
      backupBookKeyFilePath = normalizedPath(backupBookKeyFilePath, "backupBookKeyFilePath");
      Objects.requireNonNull(backupId, "backupId");
      BackupAcknowledgementState state =
          Objects.requireNonNull(acknowledgementState, "acknowledgementState");
      pairPublicationCompletion =
          ProtectedBookPairPublicationCompletion.requireBackupCompletion(
              pairPublicationCompletion, state);
      pairPublicationRetention =
          ProtectedBookPairPublicationCompletion.requireRetention(
              pairPublicationCompletion, pairPublicationRetention);
      backupFilePath = authoritativePublishedBookPath(pairPublicationRetention, backupFilePath);
      backupBookKeyFilePath =
          authoritativePublishedGeneratedSecretPath(
              pairPublicationRetention, backupBookKeyFilePath);
      if (state.requiresAttestationCommit() && attestationCommit == null) {
        throw new IllegalArgumentException(
            "A newly acknowledged backup must report its attestation operation.");
      }
      if (state.prohibitsAttestationCommit() && attestationCommit != null) {
        throw new IllegalArgumentException(
            "An already-present backup acknowledgement must not report a newly appended"
                + " operation.");
      }
    }
  }

  /** Published backup whose source-book acknowledgement needs an exact-tuple resume. */
  record AcknowledgementPending(
      Path bookFilePath,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      java.util.UUID backupId,
      ProtectedBookPairPublicationCompletion pairPublicationCompletion,
      @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention)
      implements BackupBookResult {
    public AcknowledgementPending {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      backupFilePath = normalizedPath(backupFilePath, "backupFilePath");
      backupBookKeyFilePath = normalizedPath(backupBookKeyFilePath, "backupBookKeyFilePath");
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
      @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention,
      AttestationVerificationFailure failure)
      implements BackupBookResult {
    public AcknowledgementAuthorizationRejected {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      backupFilePath = normalizedPath(backupFilePath, "backupFilePath");
      backupBookKeyFilePath = normalizedPath(backupBookKeyFilePath, "backupBookKeyFilePath");
      Objects.requireNonNull(backupId, "backupId");
      Objects.requireNonNull(pairPublicationCompletion, "pairPublicationCompletion");
      pairPublicationRetention =
          ProtectedBookPairPublicationCompletion.requireRetention(
              pairPublicationCompletion, pairPublicationRetention);
      backupFilePath = authoritativePublishedBookPath(pairPublicationRetention, backupFilePath);
      backupBookKeyFilePath =
          authoritativePublishedGeneratedSecretPath(
              pairPublicationRetention, backupBookKeyFilePath);
      failure =
          AttestationVerificationFailure.requireAdmissionFailure(
              failure, AdmissionContext.BACKUP_ACKNOWLEDGEMENT);
    }
  }

  /** Deterministic refusal for backup-book. */
  record Rejected(BookMaintenanceRejection rejection) implements BackupBookResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }

  private static Path normalizedPath(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }

  private static Path authoritativePublishedBookPath(
      @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention,
      Path backupFilePath) {
    if (pairPublicationRetention == null) {
      return backupFilePath;
    }
    return pairPublicationRetention.requireBookPublication(backupFilePath).publishedArtifactPath();
  }

  private static Path authoritativePublishedGeneratedSecretPath(
      @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention,
      Path backupBookKeyFilePath) {
    if (pairPublicationRetention == null) {
      return backupBookKeyFilePath;
    }
    return pairPublicationRetention
        .requireGeneratedSecretPublication(backupBookKeyFilePath)
        .publishedArtifactPath();
  }
}
