package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Resolves a fresh rekey target refusal only after proving the selected live source. */
final class AttestedProtectedBookRekeyAdmissionRejection {
  private AttestedProtectedBookRekeyAdmissionRejection() {}

  /**
   * Makes a target collision observable only when the caller's current live-book access is valid.
   *
   * <p>A completed matching journal is admitted before this boundary and therefore recovers with
   * its replacement key. This boundary applies only to an ordinary fresh-admission refusal, where
   * revealing an occupied generated-secret target before validating the supplied live-book key
   * would give that target precedence over an invalid source credential.
   */
  static MaintenanceDecision<ProtectedBookRekeyOutcome> resolve(
      AttestedProtectedBookMaintenanceStore store,
      ProtectedBookAccess canonicalBookAccess,
      Path bookPath,
      ProtectedBookMaintenanceRejection admissionRejection) {
    AttestedProtectedBookMaintenanceStore checkedStore = Objects.requireNonNull(store, "store");
    ProtectedBookAccess checkedBookAccess =
        Objects.requireNonNull(canonicalBookAccess, "canonicalBookAccess");
    Path checkedBookPath = Objects.requireNonNull(bookPath, "bookPath");
    ProtectedBookMaintenanceRejection checkedAdmissionRejection =
        Objects.requireNonNull(admissionRejection, "admissionRejection");
    if (!(checkedAdmissionRejection
        instanceof ProtectedBookMaintenanceRejection.SecretTargetOccupied)) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRekey(checkedAdmissionRejection);
    }
    try {
      List<Path> blocking = checkedStore.blockingArtifactsForBook(checkedBookPath);
      if (!blocking.isEmpty()) {
        return AttestedProtectedBookMaintenanceDecisions.rejectedRekey(
            new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                checkedBookPath, blocking));
      }
      try (ProtectedBookMaintenanceStore.VerifiedBook liveBook =
          AttestedProtectedBookMaintenanceDecisions.requireVerifiedBook(
              checkedStore, checkedBookAccess)) {
        AttestedProtectedBookMaintenanceDecisions.requireVerifiedLiveEvidence(
            checkedStore.loadAttestationEvidence(liveBook), checkedBookPath);
      }
      return AttestedProtectedBookMaintenanceDecisions.rejectedRekey(checkedAdmissionRejection);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRekey(exception.rejection());
    }
  }
}
