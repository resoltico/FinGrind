package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
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
    List<Path> blockingArtifacts = store.blockingArtifactsForBook(normalizedBookPath);
    if (!blockingArtifacts.isEmpty()) {
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                  normalizedBookPath, blockingArtifacts)));
    }
    if (Files.exists(normalizedBackupFilePath, LinkOption.NOFOLLOW_LINKS)) {
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(
                  normalizedBackupFilePath)));
    }
    if (Files.exists(normalizedBackupBookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BackupKeyFileAlreadyExists(
                  normalizedBackupBookKeyFilePath)));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition leaseAcquisition =
        store.acquireManagedArtifactLease(normalizedBookPath);
    if (leaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(
              support.busyArtifact(
                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, leaseBusy.artifactPath())));
    }
    try (ProtectedBookMaintenanceStore.HeldLease ignored =
        (ProtectedBookMaintenanceStore.HeldLease) leaseAcquisition) {
      return store
          .verifyInitializedBook(normalizedAccess)
          .fold(
              liveBookVerification -> {
                if (liveBookVerification
                    instanceof
                    ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
                  return MaintenanceDecision.accepted(
                      new ProtectedBookBackupOutcome.Rejected(
                          support.verificationFailed(
                              ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                              verificationFailure)));
                }
                return store
                    .stageBackupPair(
                        normalizedAccess, normalizedBackupFilePath, normalizedBackupBookKeyFilePath)
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
                                      Instant recordedAt = support.recordedAt();
                                      return store
                                          .appendMaintenanceAudit(
                                              normalizedAccess,
                                              recordedAt,
                                              ProtectedBookMaintenanceAuditKind.BACKUP_CREATED)
                                          .fold(
                                              ignoredAudit ->
                                                  commitBackedUpPair(
                                                      normalizedAccess,
                                                      recordedAt,
                                                      stagedBackupPair,
                                                      normalizedBookPath,
                                                      normalizedBackupFilePath,
                                                      normalizedBackupBookKeyFilePath),
                                              MaintenanceDecision::failed);
                                    },
                                    MaintenanceDecision::failed);
                          }
                        },
                        MaintenanceDecision::failed);
              },
              MaintenanceDecision::failed);
    }
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> commitBackedUpPair(
      ProtectedBookAccess liveBookAccess,
      Instant recordedAt,
      StagedBackupPair stagedBackupPair,
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath) {
    try {
      stagedBackupPair.commit();
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.BackedUp(
              normalizedBookPath, normalizedBackupFilePath, normalizedBackupBookKeyFilePath));
    } catch (RuntimeException commitFailure) {
      return support.compensateAuditAfterExternalCommitFailure(
          liveBookAccess,
          recordedAt,
          ProtectedBookMaintenanceAuditCompensationKind.BACKUP_CREATED,
          "Failed to publish the staged FinGrind backup pair.",
          "backupFilePath",
          commitFailure);
    }
  }
}
