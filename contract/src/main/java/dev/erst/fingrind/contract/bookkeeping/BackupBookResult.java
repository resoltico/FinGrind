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
      @Nullable ProtectedBookPairPublication pairPublication,
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
      pairPublication =
          ProtectedBookPairPublicationCompletion.requirePublication(
              pairPublicationCompletion, pairPublication);
      backupFilePath = authoritativePublishedBookPath(pairPublication, backupFilePath);
      backupBookKeyFilePath =
          authoritativePublishedGeneratedSecretPath(pairPublication, backupBookKeyFilePath);
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
      @Nullable ProtectedBookPairPublication pairPublication)
      implements BackupBookResult {
    public AcknowledgementPending {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      backupFilePath = normalizedPath(backupFilePath, "backupFilePath");
      backupBookKeyFilePath = normalizedPath(backupBookKeyFilePath, "backupBookKeyFilePath");
      Objects.requireNonNull(backupId, "backupId");
      Objects.requireNonNull(pairPublicationCompletion, "pairPublicationCompletion");
      pairPublication =
          ProtectedBookPairPublicationCompletion.requirePublication(
              pairPublicationCompletion, pairPublication);
      backupFilePath = authoritativePublishedBookPath(pairPublication, backupFilePath);
      backupBookKeyFilePath =
          authoritativePublishedGeneratedSecretPath(pairPublication, backupBookKeyFilePath);
    }
  }

  /** Published backup whose source-book acknowledgement was refused by current authorization. */
  record AcknowledgementAuthorizationRejected(
      Path bookFilePath,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      java.util.UUID backupId,
      ProtectedBookPairPublicationCompletion pairPublicationCompletion,
      @Nullable ProtectedBookPairPublication pairPublication,
      AttestationVerificationFailure failure)
      implements BackupBookResult {
    public AcknowledgementAuthorizationRejected {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      backupFilePath = normalizedPath(backupFilePath, "backupFilePath");
      backupBookKeyFilePath = normalizedPath(backupBookKeyFilePath, "backupBookKeyFilePath");
      Objects.requireNonNull(backupId, "backupId");
      Objects.requireNonNull(pairPublicationCompletion, "pairPublicationCompletion");
      pairPublication =
          ProtectedBookPairPublicationCompletion.requirePublication(
              pairPublicationCompletion, pairPublication);
      backupFilePath = authoritativePublishedBookPath(pairPublication, backupFilePath);
      backupBookKeyFilePath =
          authoritativePublishedGeneratedSecretPath(pairPublication, backupBookKeyFilePath);
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
      @Nullable ProtectedBookPairPublication pairPublication, Path backupFilePath) {
    if (pairPublication == null) {
      return backupFilePath;
    }
    return pairPublication.requireBookPublication(backupFilePath).publishedArtifactPath();
  }

  private static Path authoritativePublishedGeneratedSecretPath(
      @Nullable ProtectedBookPairPublication pairPublication, Path backupBookKeyFilePath) {
    if (pairPublication == null) {
      return backupBookKeyFilePath;
    }
    return pairPublication
        .requireGeneratedSecretPublication(backupBookKeyFilePath)
        .publishedArtifactPath();
  }
}
