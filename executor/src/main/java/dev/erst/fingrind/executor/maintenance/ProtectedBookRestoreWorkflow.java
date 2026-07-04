package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Restore-only protected-book maintenance workflow owner. */
final class ProtectedBookRestoreWorkflow {
  private final ProtectedBookMaintenanceWorkflowSupport support;

  ProtectedBookRestoreWorkflow(ProtectedBookMaintenanceWorkflowSupport support) {
    this.support = Objects.requireNonNull(support, "support");
  }

  MaintenanceDecision<ProtectedBookRestoreOutcome> restoreBook(
      Path bookFilePath, Path bookKeyFilePath, Path backupFilePath, Path backupKeyFilePath) {
    ProtectedBookMaintenanceStore store = support.store();
    Path normalizedBookPath = store.normalize(bookFilePath, "bookFilePath");
    Path normalizedBookKeyFilePath = store.normalize(bookKeyFilePath, "bookKeyFilePath");
    Path normalizedBackupFilePath = store.normalize(backupFilePath, "backupFilePath");
    Path normalizedBackupKeyFilePath = store.normalize(backupKeyFilePath, "backupKeyFilePath");
    if (normalizedBookPath.equals(normalizedBackupFilePath)) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRestoreOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook(
                  normalizedBookPath, normalizedBackupFilePath)));
    }
    List<Path> liveBookBlockingArtifacts = store.blockingArtifactsForBook(normalizedBookPath);
    if (!liveBookBlockingArtifacts.isEmpty()) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRestoreOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                  normalizedBookPath, liveBookBlockingArtifacts)));
    }
    List<Path> backupBlockingArtifacts =
        store.blockingArtifactsForBackupSource(normalizedBackupFilePath);
    if (!backupBlockingArtifacts.isEmpty()) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRestoreOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                  normalizedBackupFilePath, backupBlockingArtifacts)));
    }
    ProtectedBookAccess backupAccess =
        new ProtectedBookAccess(
            normalizedBackupFilePath,
            new ProtectedBookPassphraseSource.KeyFile(normalizedBackupKeyFilePath));
    return support.continueWithVerifiedBook(
        backupAccess,
        ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
        verifiedBackup ->
            restoreVerifiedBook(normalizedBookPath, normalizedBookKeyFilePath, verifiedBackup),
        ProtectedBookRestoreOutcome.Rejected::new);
  }

  private MaintenanceDecision<ProtectedBookRestoreOutcome> restoreVerifiedBook(
      Path normalizedBookPath,
      Path normalizedBookKeyFilePath,
      ProtectedBookMaintenanceStore.VerifiedBook verifiedBackup) {
    ProtectedBookMaintenanceStore store = support.store();
    try {
      ProtectedBookMaintenanceStore.LeaseAcquisition liveBookLeaseAcquisition =
          store.acquireManagedArtifactLease(
              normalizedBookPath, ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET);
      if (liveBookLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
        return MaintenanceDecision.accepted(
            new ProtectedBookRestoreOutcome.Rejected(
                support.busyArtifact(
                    ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, leaseBusy.artifactPath())));
      }
      ProtectedBookMaintenanceStore.LeaseAcquisition sourceLeaseAcquisition =
          store.acquireExistingArtifactLease(
              verifiedBackup.artifactPath(), ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE);
      if (sourceLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
        try (ProtectedBookMaintenanceStore.HeldLease ignored =
            (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition) {
          return MaintenanceDecision.accepted(
              new ProtectedBookRestoreOutcome.Rejected(
                  support.busyArtifact(
                      ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
                      leaseBusy.artifactPath())));
        }
      }
      try (ProtectedBookMaintenanceStore.HeldLease ignoredLiveBook =
              (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition;
          ProtectedBookMaintenanceStore.HeldLease ignoredSourceArtifact =
              (ProtectedBookMaintenanceStore.HeldLease) sourceLeaseAcquisition) {
        return store
            .stageRestoredBookPair(verifiedBackup, normalizedBookPath, normalizedBookKeyFilePath)
            .fold(
                stagedRestoredBookPair ->
                    commitRestoredBookPair(
                        normalizedBookPath, normalizedBookKeyFilePath, stagedRestoredBookPair),
                MaintenanceDecision::failed);
      }
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      if (exception.rejection()
          instanceof ProtectedBookMaintenanceRejection.ArtifactPathInvalid pathInvalid) {
        return MaintenanceDecision.accepted(new ProtectedBookRestoreOutcome.Rejected(pathInvalid));
      }
      throw new IllegalArgumentException(
          "Expected one maintenance artifact-path rejection, but received: "
              + exception.rejection(),
          exception);
    }
  }

  private MaintenanceDecision<ProtectedBookRestoreOutcome> commitRestoredBookPair(
      Path normalizedBookPath,
      Path normalizedBookKeyFilePath,
      StagedRestoredBookPair stagedRestoredBookPair) {
    try (StagedRestoredBookPair ignored = stagedRestoredBookPair) {
      return stagedRestoredBookPair
          .verifyInitializedRestoredBook()
          .fold(
              verification -> {
                if (verification
                    instanceof
                    ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
                  return MaintenanceDecision.accepted(
                      new ProtectedBookRestoreOutcome.Rejected(
                          support.verificationFailed(
                              ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                              verificationFailure)));
                }
                try (ProtectedBookMaintenanceStore.VerifiedBook verifiedRestoredBook =
                    (ProtectedBookMaintenanceStore.VerifiedBook) verification) {
                  return support
                      .store()
                      .appendMaintenanceAudit(
                          verifiedRestoredBook,
                          support.recordedAt(),
                          ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED)
                      .fold(
                          ignoredAudit -> {
                            stagedRestoredBookPair.commit();
                            return MaintenanceDecision.accepted(
                                new ProtectedBookRestoreOutcome.Restored(
                                    normalizedBookPath, normalizedBookKeyFilePath));
                          },
                          MaintenanceDecision::failed);
                }
              },
              MaintenanceDecision::failed);
    }
  }
}
