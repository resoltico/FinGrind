package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailurePaths;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBookReplacement;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Shared support seam for protected-book maintenance workflow families. */
final class ProtectedBookMaintenanceWorkflowSupport {
  private final Clock clock;
  private final ProtectedBookMaintenanceStore store;

  ProtectedBookMaintenanceWorkflowSupport(Clock clock, ProtectedBookMaintenanceStore store) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.store = Objects.requireNonNull(store, "store");
  }

  ProtectedBookMaintenanceStore store() {
    return store;
  }

  Instant recordedAt() {
    return clock.instant();
  }

  ProtectedBookMaintenanceRejection.ArtifactVerificationFailed verificationFailed(
      ProtectedBookMaintenanceArtifactRole artifactRole,
      ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
    return new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
        artifactRole, verificationFailure.artifactPath(), verificationFailure.failure());
  }

  ProtectedBookMaintenanceRejection.ArtifactBusy busyArtifact(
      ProtectedBookMaintenanceArtifactRole artifactRole, Path artifactPath) {
    return new ProtectedBookMaintenanceRejection.ArtifactBusy(artifactRole, artifactPath);
  }

  <T> MaintenanceDecision<T> storageFailure(Path path, String message, String argumentName) {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(argumentName, "argumentName");
    return MaintenanceDecision.failed(
        new MaintenanceFailure(
            ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
            message,
            "Inspect the selected filesystem path and retry after resolving the underlying storage problem.",
            argumentName,
            ContractFailurePaths.primary(path)));
  }

  <T> MaintenanceDecision<T> compensateAuditAfterExternalCommitFailure(
      ProtectedBookMaintenanceStore.VerifiedBook verifiedBook,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditCompensationKind auditKind,
      Path path,
      String failureMessage,
      String argumentName) {
    Objects.requireNonNull(path, "path");
    return store
        .appendMaintenanceAuditCompensation(verifiedBook, recordedAt, auditKind)
        .fold(
            ignoredCompletion ->
                MaintenanceDecision.failed(
                    new MaintenanceFailure(
                        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
                        failureMessage,
                        "Inspect the selected filesystem path and retry after resolving the underlying storage problem.",
                        argumentName,
                        ContractFailurePaths.primary(path))),
            MaintenanceDecision::failed);
  }

  ArtifactSelection selectRollbackArtifact(
      Path normalizedBookPath, @Nullable Path rollbackArtifactPath) {
    List<Path> rollbackArtifacts = store.staleRollbackArtifacts(normalizedBookPath);
    if (rollbackArtifactPath == null) {
      if (rollbackArtifacts.isEmpty()) {
        return new RejectedArtifact(
            new ProtectedBookMaintenanceRejection.NoRollbackArtifactsFound(normalizedBookPath));
      }
      if (rollbackArtifacts.size() > 1) {
        return new RejectedArtifact(
            new ProtectedBookMaintenanceRejection.RollbackArtifactSelectionRequired(
                normalizedBookPath, rollbackArtifacts));
      }
      return new SelectedArtifact(rollbackArtifacts.getFirst());
    }
    Path normalizedRollbackArtifactPath =
        store.normalize(rollbackArtifactPath, "rollbackArtifactPath");
    if (!Files.exists(normalizedRollbackArtifactPath, LinkOption.NOFOLLOW_LINKS)) {
      return new RejectedArtifact(
          new ProtectedBookMaintenanceRejection.RollbackArtifactNotFound(
              normalizedRollbackArtifactPath));
    }
    if (!store.isRollbackArtifactForBook(normalizedBookPath, normalizedRollbackArtifactPath)) {
      return new RejectedArtifact(
          new ProtectedBookMaintenanceRejection.RollbackArtifactNotForBook(
              normalizedBookPath, normalizedRollbackArtifactPath));
    }
    return new SelectedArtifact(normalizedRollbackArtifactPath);
  }

  <T> MaintenanceDecision<T> continueWithVerifiedBook(
      ProtectedBookAccess bookAccess,
      ProtectedBookMaintenanceArtifactRole artifactRole,
      Function<ProtectedBookMaintenanceStore.VerifiedBook, MaintenanceDecision<T>> verifiedAction,
      Function<ProtectedBookMaintenanceRejection, T> rejectedOutcome) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(artifactRole, "artifactRole");
    Objects.requireNonNull(verifiedAction, "verifiedAction");
    Objects.requireNonNull(rejectedOutcome, "rejectedOutcome");
    try {
      return store
          .verifyInitializedBook(bookAccess, artifactRole)
          .fold(
              verification -> {
                if (verification
                    instanceof
                    ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
                  return MaintenanceDecision.accepted(
                      rejectedOutcome.apply(verificationFailed(artifactRole, verificationFailure)));
                }
                try (ProtectedBookMaintenanceStore.VerifiedBook verifiedBook =
                    (ProtectedBookMaintenanceStore.VerifiedBook) verification) {
                  return verifiedAction.apply(verifiedBook);
                }
              },
              MaintenanceDecision::failed);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return MaintenanceDecision.accepted(rejectedOutcome.apply(exception.rejection()));
    }
  }

  <T> MaintenanceDecision<T> restoreVerifiedSourceArtifact(
      Path normalizedBookPath,
      ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook,
      ProtectedBookMaintenanceArtifactRole sourceArtifactRole,
      ProtectedBookMaintenanceAuditKind auditKind,
      Function<ProtectedBookMaintenanceRejection, T> rejectedOutcome,
      Supplier<T> restoredOutcome) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    Objects.requireNonNull(verifiedSourceBook, "verifiedSourceBook");
    Objects.requireNonNull(sourceArtifactRole, "sourceArtifactRole");
    Objects.requireNonNull(auditKind, "auditKind");
    Objects.requireNonNull(rejectedOutcome, "rejectedOutcome");
    Objects.requireNonNull(restoredOutcome, "restoredOutcome");
    try {
      ProtectedBookMaintenanceStore.LeaseAcquisition liveBookLeaseAcquisition =
          store.acquireManagedArtifactLease(
              normalizedBookPath, ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET);
      if (liveBookLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
        return MaintenanceDecision.accepted(
            rejectedOutcome.apply(
                busyArtifact(
                    ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, leaseBusy.artifactPath())));
      }
      ProtectedBookMaintenanceStore.LeaseAcquisition sourceLeaseAcquisition =
          store.acquireExistingArtifactLease(verifiedSourceBook.artifactPath(), sourceArtifactRole);
      if (sourceLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
        try (ProtectedBookMaintenanceStore.HeldLease ignored =
            (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition) {
          return MaintenanceDecision.accepted(
              rejectedOutcome.apply(busyArtifact(sourceArtifactRole, leaseBusy.artifactPath())));
        }
      }
      try (ProtectedBookMaintenanceStore.HeldLease ignoredLiveBook =
              (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition;
          ProtectedBookMaintenanceStore.HeldLease ignoredSourceArtifact =
              (ProtectedBookMaintenanceStore.HeldLease) sourceLeaseAcquisition;
          StagedBookReplacement stagedReplacement =
              store.stageReplacement(verifiedSourceBook.artifactPath(), normalizedBookPath)) {
        return store
            .verifyInitializedReplica(stagedReplacement.stagedBookPath(), verifiedSourceBook)
            .fold(
                verification -> {
                  if (verification
                      instanceof
                      ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
                    return MaintenanceDecision.accepted(
                        rejectedOutcome.apply(
                            verificationFailed(
                                ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                                verificationFailure)));
                  }
                  try (ProtectedBookMaintenanceStore.VerifiedBook verifiedStagedBook =
                      (ProtectedBookMaintenanceStore.VerifiedBook) verification) {
                    return store
                        .appendMaintenanceAudit(verifiedStagedBook, recordedAt(), auditKind)
                        .fold(
                            ignoredAudit -> {
                              stagedReplacement.commit();
                              return MaintenanceDecision.accepted(restoredOutcome.get());
                            },
                            MaintenanceDecision::failed);
                  }
                },
                MaintenanceDecision::failed);
      }
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return MaintenanceDecision.accepted(rejectedOutcome.apply(exception.rejection()));
    }
  }

  /** Local rollback-artifact selection result before projection into public outcomes. */
  sealed interface ArtifactSelection permits SelectedArtifact, RejectedArtifact {}

  /** Local selected rollback artifact that passed existence and sibling checks. */
  static final class SelectedArtifact implements ArtifactSelection {
    private final Path rollbackArtifactPath;

    private SelectedArtifact(Path rollbackArtifactPath) {
      this.rollbackArtifactPath =
          Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }

    Path rollbackArtifactPath() {
      return rollbackArtifactPath;
    }
  }

  /** Local rejected rollback-artifact selection carrying one deterministic refusal. */
  static final class RejectedArtifact implements ArtifactSelection {
    private final ProtectedBookMaintenanceRejection rejection;

    private RejectedArtifact(ProtectedBookMaintenanceRejection rejection) {
      this.rejection = Objects.requireNonNull(rejection, "rejection");
    }

    ProtectedBookMaintenanceRejection rejection() {
      return rejection;
    }
  }
}
