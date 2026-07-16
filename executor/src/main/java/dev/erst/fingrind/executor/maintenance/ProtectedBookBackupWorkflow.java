package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Backup-only protected-book maintenance workflow owner. */
final class ProtectedBookBackupWorkflow {
  private final ProtectedBookMaintenanceWorkflowSupport support;

  ProtectedBookBackupWorkflow(ProtectedBookMaintenanceWorkflowSupport support) {
    this.support = Objects.requireNonNull(support, "support");
  }

  MaintenanceDecision<ProtectedBookBackupOutcome> backupBook(
      ProtectedBookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    ProtectedBookMaintenanceStore store = support.store();
    Path normalizedBookPath = store.normalize(bookAccess.bookFilePath(), "bookFilePath");
    ProtectedBookAccess normalizedAccess =
        new ProtectedBookAccess(normalizedBookPath, bookAccess.passphraseSource());
    Path normalizedBackupFilePath = store.normalize(backupFilePath, "backupFilePath");
    Path normalizedBackupBookKeyFilePath =
        store.normalize(backupBookKeyFilePath, "backupBookKeyFilePath");
    PreparedPairPublication preparedPublication;
    try {
      preparedPublication =
          store.preparePairPublication(
              normalizedBackupBookKeyFilePath,
              normalizedBackupFilePath,
              RestoredBookTargetPolicy.REQUIRE_ABSENT,
              ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
              ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(exception.rejection()));
    } catch (RuntimeException recoveryFailure) {
      return support.storageFailure(
          normalizedBackupFilePath,
          "Failed to recover or prepare the FinGrind backup pair publication.",
          "backupFilePath");
    }
    try (preparedPublication) {
      return backupWithPreparedPublication(
          normalizedAccess, normalizedBookPath, store, preparedPublication);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(exception.rejection()));
    } catch (RuntimeException workflowFailure) {
      return support.storageFailure(
          normalizedBackupFilePath,
          "Failed to verify, stage, or publish the FinGrind backup pair.",
          "backupFilePath");
    }
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> backupWithPreparedPublication(
      ProtectedBookAccess normalizedAccess,
      Path normalizedBookPath,
      ProtectedBookMaintenanceStore store,
      PreparedPairPublication preparedPublication) {
    Path normalizedBackupFilePath = preparedPublication.bookTargetPath();
    Path normalizedBackupBookKeyFilePath = preparedPublication.secretTargetPath();
    List<Path> blockingArtifacts = store.blockingArtifactsForBook(normalizedBookPath);
    if (!blockingArtifacts.isEmpty()) {
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                  normalizedBookPath, blockingArtifacts)));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition leaseAcquisition =
        store.acquireManagedArtifactLease(
            normalizedBookPath, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
    if (leaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(
              support.busyArtifact(
                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, leaseBusy.artifactPath())));
    }
    try (ProtectedBookMaintenanceStore.HeldLease ignored =
        (ProtectedBookMaintenanceStore.HeldLease) leaseAcquisition) {
      return support.continueWithVerifiedBook(
          normalizedAccess,
          ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
          verifiedLiveBook ->
              store
                  .stageBackupPair(verifiedLiveBook, preparedPublication)
                  .fold(
                      stagedBackupPair -> {
                        try (StagedBackupPair ignoredStaged = stagedBackupPair) {
                          return stagedBackupPair
                              .verifyInitializedBackup()
                              .fold(
                                  verification -> {
                                    if (verification
                                        instanceof
                                        ProtectedBookMaintenanceStore.VerificationFailure
                                            verificationFailure) {
                                      return MaintenanceDecision.accepted(
                                          new ProtectedBookBackupOutcome.Rejected(
                                              support.verificationFailed(
                                                  ProtectedBookMaintenanceArtifactRole
                                                      .BACKUP_SOURCE,
                                                  verificationFailure)));
                                    }
                                    return commitBackedUpPair(
                                        stagedBackupPair,
                                        normalizedBookPath,
                                        normalizedBackupFilePath,
                                        normalizedBackupBookKeyFilePath);
                                  },
                                  MaintenanceDecision::failed);
                        }
                      },
                      MaintenanceDecision::failed),
          ProtectedBookBackupOutcome.Rejected::new);
    }
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> commitBackedUpPair(
      StagedBackupPair stagedBackupPair,
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath) {
    try {
      stagedBackupPair.commit();
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.BackedUp(
              normalizedBookPath, normalizedBackupFilePath, normalizedBackupBookKeyFilePath));
    } catch (ProtectedBookMaintenanceRejectionException rejection) {
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(rejection.rejection()));
    } catch (RuntimeException commitFailure) {
      return support.storageFailure(
          normalizedBackupFilePath,
          "Failed to publish the staged FinGrind backup pair.",
          "backupFilePath");
    }
  }
}
