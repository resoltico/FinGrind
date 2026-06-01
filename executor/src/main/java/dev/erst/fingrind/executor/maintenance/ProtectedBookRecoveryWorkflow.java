package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedRollbackArtifactDeletion;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Rollback recovery workflow owner for inspection, restore, and deletion paths. */
final class ProtectedBookRecoveryWorkflow {
  private final ProtectedBookMaintenanceWorkflowSupport support;

  ProtectedBookRecoveryWorkflow(ProtectedBookMaintenanceWorkflowSupport support) {
    this.support = Objects.requireNonNull(support, "support");
  }

  MaintenanceDecision<ProtectedBookRecoveryOutcome> inspectRollbackArtifacts(Path bookFilePath) {
    ProtectedBookMaintenanceStore store = support.store();
    Path normalizedBookPath = store.normalize(bookFilePath, "bookFilePath");
    return MaintenanceDecision.accepted(
        new ProtectedBookRecoveryOutcome.Inspected(
            normalizedBookPath, store.staleRollbackArtifacts(normalizedBookPath)));
  }

  MaintenanceDecision<ProtectedBookRecoveryOutcome> deleteRollbackArtifact(
      ProtectedBookAccess bookAccess, @Nullable Path rollbackArtifactPath) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    ProtectedBookMaintenanceStore store = support.store();
    Path normalizedBookPath = store.normalize(bookAccess.bookFilePath(), "bookFilePath");
    ProtectedBookAccess normalizedAccess =
        new ProtectedBookAccess(normalizedBookPath, bookAccess.passphraseSource());
    ProtectedBookMaintenanceWorkflowSupport.ArtifactSelection selection =
        support.selectRollbackArtifact(normalizedBookPath, rollbackArtifactPath);
    if (selection instanceof ProtectedBookMaintenanceWorkflowSupport.RejectedArtifact rejected) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(rejected.rejection()));
    }
    Path selectedRollbackArtifact =
        ((ProtectedBookMaintenanceWorkflowSupport.SelectedArtifact) selection)
            .rollbackArtifactPath();
    return support.continueWithVerifiedBook(
        normalizedAccess,
        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
        ignoredVerified ->
            deleteVerifiedRollbackArtifact(
                normalizedBookPath, normalizedAccess, selectedRollbackArtifact),
        ProtectedBookRecoveryOutcome.Rejected::new);
  }

  MaintenanceDecision<ProtectedBookRecoveryOutcome> restoreRollbackArtifact(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      ProtectedBookPassphraseSource expectedPassphraseSource) {
    ProtectedBookMaintenanceStore store = support.store();
    Path normalizedBookPath = store.normalize(bookFilePath, "bookFilePath");
    ProtectedBookMaintenanceWorkflowSupport.ArtifactSelection selection =
        support.selectRollbackArtifact(normalizedBookPath, rollbackArtifactPath);
    if (selection instanceof ProtectedBookMaintenanceWorkflowSupport.RejectedArtifact rejected) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(rejected.rejection()));
    }
    List<Path> liveBookBlockingArtifacts =
        store.blockingArtifactsForBook(normalizedBookPath).stream()
            .filter(path -> !store.isRollbackArtifactForBook(normalizedBookPath, path))
            .toList();
    if (!liveBookBlockingArtifacts.isEmpty()) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                  normalizedBookPath, liveBookBlockingArtifacts)));
    }
    Path selectedRollbackArtifact =
        ((ProtectedBookMaintenanceWorkflowSupport.SelectedArtifact) selection)
            .rollbackArtifactPath();
    ProtectedBookAccess rollbackAccess =
        new ProtectedBookAccess(selectedRollbackArtifact, expectedPassphraseSource);
    return support.continueWithVerifiedBook(
        rollbackAccess,
        ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
        ignoredVerified ->
            restoreVerifiedRollbackArtifact(
                normalizedBookPath, rollbackAccess, selectedRollbackArtifact),
        ProtectedBookRecoveryOutcome.Rejected::new);
  }

  private MaintenanceDecision<ProtectedBookRecoveryOutcome> restoreVerifiedRollbackArtifact(
      Path normalizedBookPath, ProtectedBookAccess rollbackAccess, Path selectedRollbackArtifact) {
    return support.restoreVerifiedSourceArtifact(
        normalizedBookPath,
        selectedRollbackArtifact,
        rollbackAccess.passphraseSource(),
        ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
        ProtectedBookMaintenanceAuditKind.REKEY_ROLLBACK_RESTORED,
        ProtectedBookRecoveryOutcome.Rejected::new,
        () ->
            new ProtectedBookRecoveryOutcome.Restored(
                normalizedBookPath, selectedRollbackArtifact));
  }

  private MaintenanceDecision<ProtectedBookRecoveryOutcome> deleteVerifiedRollbackArtifact(
      Path normalizedBookPath, ProtectedBookAccess liveBookAccess, Path selectedRollbackArtifact) {
    ProtectedBookMaintenanceStore store = support.store();
    ProtectedBookMaintenanceStore.LeaseAcquisition liveBookLeaseAcquisition =
        store.acquireExistingArtifactLease(normalizedBookPath);
    if (liveBookLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(
              support.busyArtifact(
                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, leaseBusy.artifactPath())));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition rollbackLeaseAcquisition =
        store.acquireExistingArtifactLease(selectedRollbackArtifact);
    if (rollbackLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      try (ProtectedBookMaintenanceStore.HeldLease ignored =
          (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition) {
        return MaintenanceDecision.accepted(
            new ProtectedBookRecoveryOutcome.Rejected(
                support.busyArtifact(
                    ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
                    leaseBusy.artifactPath())));
      }
    }
    try (ProtectedBookMaintenanceStore.HeldLease ignoredLiveBook =
            (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition;
        ProtectedBookMaintenanceStore.HeldLease ignoredRollbackArtifact =
            (ProtectedBookMaintenanceStore.HeldLease) rollbackLeaseAcquisition;
        StagedRollbackArtifactDeletion stagedDeletion =
            store.stageRollbackArtifactDeletion(selectedRollbackArtifact)) {
      Instant recordedAt = support.recordedAt();
      return store
          .appendMaintenanceAudit(
              liveBookAccess, recordedAt, ProtectedBookMaintenanceAuditKind.REKEY_ROLLBACK_DELETED)
          .fold(
              ignoredAudit ->
                  commitDeletedRollbackArtifact(
                      normalizedBookPath,
                      liveBookAccess,
                      recordedAt,
                      selectedRollbackArtifact,
                      stagedDeletion),
              MaintenanceDecision::failed);
    }
  }

  private MaintenanceDecision<ProtectedBookRecoveryOutcome> commitDeletedRollbackArtifact(
      Path normalizedBookPath,
      ProtectedBookAccess liveBookAccess,
      Instant recordedAt,
      Path selectedRollbackArtifact,
      StagedRollbackArtifactDeletion stagedDeletion) {
    try {
      stagedDeletion.commit();
      return MaintenanceDecision.accepted(
          new ProtectedBookRecoveryOutcome.Deleted(normalizedBookPath, selectedRollbackArtifact));
    } catch (RuntimeException commitFailure) {
      return support.compensateAuditAfterExternalCommitFailure(
          liveBookAccess,
          recordedAt,
          ProtectedBookMaintenanceAuditCompensationKind.REKEY_ROLLBACK_DELETED,
          "Failed to delete the staged FinGrind rollback artifact.",
          "rollbackArtifactPath",
          commitFailure);
    }
  }
}
