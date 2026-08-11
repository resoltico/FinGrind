package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.ContractFailurePaths;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerificationException;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Maps shared protected-book storage outcomes to maintenance decisions. */
final class AttestedProtectedBookMaintenanceDecisions {
  private AttestedProtectedBookMaintenanceDecisions() {}

  static ProtectedBookMaintenanceStore.VerifiedBook requireVerifiedBook(
      AttestedProtectedBookMaintenanceStore store, ProtectedBookAccess access) {
    ProtectedBookMaintenanceStore.BookVerification verification =
        store
            .verifyInitializedBook(access, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)
            .fold(
                value -> value,
                failure -> {
                  throw new ContractFailureException(failure.toContractFailure());
                });
    if (verification instanceof ProtectedBookMaintenanceStore.VerifiedBook verifiedBook) {
      return verifiedBook;
    }
    ProtectedBookMaintenanceStore.VerificationFailure failure =
        (ProtectedBookMaintenanceStore.VerificationFailure) verification;
    throw new ProtectedBookMaintenanceRejectionException(
        verificationFailed(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, failure));
  }

  /**
   * Verifies all persisted live evidence before any lifecycle candidate is classified or signed.
   *
   * <p>A failure here is historical protected-book corruption, even when its terminal core cause is
   * an authorization rule. Candidate authorization is only meaningful after this invariant has
   * held.
   */
  static AttestationVerification requireVerifiedLiveEvidence(
      List<AttestationEvidence> evidence, Path bookPath) {
    try {
      return AttestationVerifier.verifyBook(
          List.copyOf(Objects.requireNonNull(evidence, "evidence")));
    } catch (AttestationVerificationException exception) {
      throw rejectHistoricalLiveEvidence(bookPath, exception);
    }
  }

  private static ProtectedBookMaintenanceRejectionException rejectHistoricalLiveEvidence(
      Path bookPath, Throwable cause) {
    return new ProtectedBookMaintenanceRejectionException(
        new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            Objects.requireNonNull(bookPath, "bookPath"),
            ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED),
        Objects.requireNonNull(cause, "cause"));
  }

  static ProtectedBookMaintenanceRejection.ArtifactVerificationFailed verificationFailed(
      ProtectedBookMaintenanceArtifactRole role,
      ProtectedBookMaintenanceStore.VerificationFailure failure) {
    return new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
        role, failure.artifactPath(), failure.failure());
  }

  static MaintenanceDecision<ProtectedBookBackupOutcome> rejectedBackup(
      ProtectedBookMaintenanceRejection rejection) {
    return MaintenanceDecision.accepted(new ProtectedBookBackupOutcome.Rejected(rejection));
  }

  static MaintenanceDecision<ProtectedBookRestoreOutcome> rejectedRestore(
      ProtectedBookMaintenanceRejection rejection) {
    return MaintenanceDecision.accepted(new ProtectedBookRestoreOutcome.Rejected(rejection));
  }

  static MaintenanceDecision<ProtectedBookRekeyOutcome> rejectedRekey(
      ProtectedBookMaintenanceRejection rejection) {
    return MaintenanceDecision.accepted(new ProtectedBookRekeyOutcome.Rejected(rejection));
  }

  static <T> MaintenanceDecision<T> failure(Path path, String argument, String message) {
    return MaintenanceDecision.failed(
        new MaintenanceFailure(
            ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
            message,
            "Inspect the selected path and retry after resolving the underlying storage condition.",
            argument,
            ContractFailurePaths.primary(path),
            null));
  }

  /** Raises the public failure for retained pair evidence with no safe final-member fact. */
  static ContractFailureException pairPublicationEvidenceBlocked(
      Path bookTargetPath,
      ProtectedBookPairPublicationMemberState bookTargetState,
      Path generatedSecretTargetPath,
      ProtectedBookPairPublicationMemberState generatedSecretTargetState) {
    ContractFailureDetails.PairPublication pairPublication =
        new ContractFailureDetails.PairPublication(
            new ContractFailureDetails.PairPublicationMember(bookTargetPath, bookTargetState),
            new ContractFailureDetails.PairPublicationMember(
                generatedSecretTargetPath, generatedSecretTargetState));
    return new ContractFailureException(
        ContractErrors.protectedBookPairPublicationEvidenceBlockedFailure(pairPublication));
  }
}
