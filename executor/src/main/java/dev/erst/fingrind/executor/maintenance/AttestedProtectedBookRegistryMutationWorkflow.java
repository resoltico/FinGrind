package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldLease;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.LeaseAcquisition;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Owns live-book admission for one attested credential-registry or policy mutation. */
final class AttestedProtectedBookRegistryMutationWorkflow {
  private final Clock clock;
  private final AttestedProtectedBookMaintenanceStore store;

  AttestedProtectedBookRegistryMutationWorkflow(
      Clock clock, AttestedProtectedBookMaintenanceStore store) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.store = Objects.requireNonNull(store, "store");
  }

  MaintenanceDecision<ProtectedBookRegistryMutationOutcome> mutate(
      ProtectedBookAccess bookAccess,
      AttestationRegistryMutation mutation,
      AttestationSigningSession signingSession) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    AttestationRegistryMutation checkedMutation = Objects.requireNonNull(mutation, "mutation");
    Objects.requireNonNull(signingSession, "signingSession");
    Path bookPath = store.normalize(bookAccess.bookFilePath(), "bookFilePath");
    List<Path> blocking = store.blockingArtifactsForBook(bookPath);
    if (!blocking.isEmpty()) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRegistryMutationOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(bookPath, blocking)));
    }
    LeaseAcquisition lease =
        store.acquireManagedArtifactLease(bookPath, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
    if (lease instanceof ProtectedBookMaintenanceStore.LeaseBusy busy) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRegistryMutationOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.ArtifactBusy(
                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactPath())));
    }
    HeldLease heldLease = (HeldLease) lease;
    try {
      return MaintenanceResourceScope.closeAfter(
          heldLease::close,
          () -> {
            ProtectedBookMaintenanceStore.VerifiedBook liveBook =
                AttestedProtectedBookMaintenanceDecisions.requireVerifiedBook(store, bookAccess);
            return MaintenanceResourceScope.closeAfter(
                liveBook::close,
                () -> {
                  AttestationVerification verification =
                      store.appendAttestedOperation(
                          liveBook,
                          checkedMutation.operationKind(),
                          clock.instant(),
                          checkedMutation.preimages(),
                          signingSession,
                          null);
                  return MaintenanceDecision.accepted(
                      new ProtectedBookRegistryMutationOutcome.Mutated(
                          bookPath,
                          checkedMutation.operationKind().wireToken(),
                          verification.headOrder()));
                });
          });
    } catch (AttestationStaleHeadException exception) {
      throw exception;
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRegistryMutationOutcome.Rejected(exception.rejection()));
    } catch (RuntimeException exception) {
      Optional<AttestationAuthorizationFailure> authorizationFailure =
          historicalAuthorizationFailure(exception);
      if (authorizationFailure.isPresent()) {
        return MaintenanceDecision.accepted(
            new ProtectedBookRegistryMutationOutcome.AuthorizationRejected(
                authorizationFailure.orElseThrow()));
      }
      return AttestedProtectedBookMaintenanceDecisions.failure(
          bookPath, "bookFilePath", "Failed to append the attested credential or policy mutation.");
    }
  }

  private static Optional<AttestationAuthorizationFailure> historicalAuthorizationFailure(
      RuntimeException exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof AttestationAdmissionRejectedException admissionRejected) {
        return Optional.of(admissionRejected.failure());
      }
      if (cause instanceof AttestationAuthorizationException authorizationException) {
        return Optional.of(authorizationException.failure());
      }
    }
    return Optional.empty();
  }
}
