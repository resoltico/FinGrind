package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailurePaths;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;

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
                  throw new IllegalStateException(failure.message());
                });
    if (verification instanceof ProtectedBookMaintenanceStore.VerifiedBook verifiedBook) {
      return verifiedBook;
    }
    ProtectedBookMaintenanceStore.VerificationFailure failure =
        (ProtectedBookMaintenanceStore.VerificationFailure) verification;
    throw new ProtectedBookMaintenanceRejectionException(
        verificationFailed(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, failure));
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
            ContractFailurePaths.primary(path)));
  }
}
