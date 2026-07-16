package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
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
      Path bookFilePath,
      Path newBookKeyFilePath,
      Path backupFilePath,
      Path backupKeyFilePath,
      boolean replaceExistingBook) {
    ProtectedBookMaintenanceStore store = support.store();
    Path normalizedBookPath = store.normalize(bookFilePath, "bookFilePath");
    Path normalizedNewBookKeyFilePath = store.normalize(newBookKeyFilePath, "newBookKeyFilePath");
    Path normalizedBackupFilePath = store.normalize(backupFilePath, "backupFilePath");
    Path normalizedBackupKeyFilePath = store.normalize(backupKeyFilePath, "backupKeyFilePath");
    if (normalizedBookPath.equals(normalizedBackupFilePath)) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRestoreOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook(
                  normalizedBookPath, normalizedBackupFilePath)));
    }
    try (PreparedPairPublication preparedPublication =
        store.preparePairPublication(
            normalizedNewBookKeyFilePath,
            normalizedBookPath,
            replaceExistingBook
                ? RestoredBookTargetPolicy.REPLACE_SELECTED
                : RestoredBookTargetPolicy.REQUIRE_ABSENT,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET)) {
      return restoreWithPreparedPublication(
          normalizedBackupFilePath, normalizedBackupKeyFilePath, store, preparedPublication);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRestoreOutcome.Rejected(exception.rejection()));
    } catch (RuntimeException recoveryFailure) {
      return support.storageFailure(
          normalizedBookPath,
          "Failed to recover or prepare the FinGrind restored-book pair publication.",
          "bookFilePath");
    }
  }

  private MaintenanceDecision<ProtectedBookRestoreOutcome> restoreWithPreparedPublication(
      Path normalizedBackupFilePath,
      Path normalizedBackupKeyFilePath,
      ProtectedBookMaintenanceStore store,
      PreparedPairPublication preparedPublication) {
    Path normalizedBookPath = preparedPublication.bookTargetPath();
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
            restoreVerifiedBook(normalizedBookPath, preparedPublication, verifiedBackup),
        ProtectedBookRestoreOutcome.Rejected::new);
  }

  private MaintenanceDecision<ProtectedBookRestoreOutcome> restoreVerifiedBook(
      Path normalizedBookPath,
      PreparedPairPublication preparedPublication,
      ProtectedBookMaintenanceStore.VerifiedBook verifiedBackup) {
    ProtectedBookMaintenanceStore store = support.store();
    try {
      ProtectedBookMaintenanceStore.LeaseAcquisition sourceLeaseAcquisition =
          store.acquireExistingArtifactLease(
              verifiedBackup.artifactPath(), ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE);
      if (sourceLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
        return MaintenanceDecision.accepted(
            new ProtectedBookRestoreOutcome.Rejected(
                support.busyArtifact(
                    ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, leaseBusy.artifactPath())));
      }
      try (ProtectedBookMaintenanceStore.HeldLease ignoredSourceArtifact =
          (ProtectedBookMaintenanceStore.HeldLease) sourceLeaseAcquisition) {
        return store
            .stageRestoredBookPair(verifiedBackup, preparedPublication)
            .fold(
                stagedRestoredBookPair ->
                    commitRestoredBookPair(
                        normalizedBookPath,
                        preparedPublication.secretTargetPath(),
                        stagedRestoredBookPair),
                MaintenanceDecision::failed);
      }
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRestoreOutcome.Rejected(exception.rejection()));
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
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRestoreOutcome.Rejected(exception.rejection()));
    } catch (RuntimeException commitFailure) {
      return support.storageFailure(
          normalizedBookPath,
          "Failed to publish the staged FinGrind restored-book pair.",
          "bookFilePath");
    }
  }
}
