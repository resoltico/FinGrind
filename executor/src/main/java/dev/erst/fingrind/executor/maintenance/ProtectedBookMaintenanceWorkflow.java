package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Local workflow that owns protected-book maintenance semantics behind the public adapter. */
public final class ProtectedBookMaintenanceWorkflow {
  private final Clock clock;
  private final ProtectedBookMaintenanceStore store;

  /** Creates one maintenance workflow over the clock and protected-book store seams. */
  public ProtectedBookMaintenanceWorkflow(Clock clock, ProtectedBookMaintenanceStore store) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.store = Objects.requireNonNull(store, "store");
  }

  /** Exports one verified encrypted backup pair for the selected live book. */
  public MaintenanceDecision<ProtectedBookBackupOutcome> backupBook(
      ProtectedBookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
    Objects.requireNonNull(bookAccess, "bookAccess");
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
              busyArtifact(
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
                          verificationFailed(
                              ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                              verificationFailure)));
                }
                return store
                    .stageBackupPair(
                        normalizedAccess, normalizedBackupFilePath, normalizedBackupBookKeyFilePath)
                    .fold(
                        stagedBackupPair -> {
                          try (ProtectedBookMaintenanceStore.StagedBackupPair ignoredStaged =
                              stagedBackupPair) {
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
                                                verificationFailed(
                                                    ProtectedBookMaintenanceArtifactRole
                                                        .BACKUP_SOURCE,
                                                    verificationFailure)));
                                      }
                                      Instant recordedAt = clock.instant();
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

  /** Restores one verified encrypted backup pair over the selected live book path. */
  public MaintenanceDecision<ProtectedBookRestoreOutcome> restoreBook(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    Path normalizedBookPath = store.normalize(bookFilePath, "bookFilePath");
    Path normalizedBackupFilePath = store.normalize(backupFilePath, "backupFilePath");
    Path normalizedBackupBookKeyFilePath =
        store.normalize(backupBookKeyFilePath, "backupBookKeyFilePath");
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
            new ProtectedBookPassphraseSource.KeyFile(normalizedBackupBookKeyFilePath));
    MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification> preVerification =
        store.verifyInitializedBook(backupAccess);
    return preVerification.fold(
        verification -> {
          if (verification instanceof ProtectedBookMaintenanceStore.VerificationFailure failure) {
            return MaintenanceDecision.accepted(
                new ProtectedBookRestoreOutcome.Rejected(
                    verificationFailed(
                        ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, failure)));
          }
          return restoreVerifiedBook(
              normalizedBookPath, normalizedBackupFilePath, normalizedBackupBookKeyFilePath);
        },
        MaintenanceDecision::failed);
  }

  /** Lists every sibling rollback artifact for the selected live book without mutating state. */
  public MaintenanceDecision<ProtectedBookRecoveryOutcome> inspectRollbackArtifacts(
      Path bookFilePath) {
    Path normalizedBookPath = store.normalize(bookFilePath, "bookFilePath");
    return MaintenanceDecision.accepted(
        new ProtectedBookRecoveryOutcome.Inspected(
            normalizedBookPath, store.staleRollbackArtifacts(normalizedBookPath)));
  }

  /** Deletes one verified rollback artifact for the selected live book. */
  public MaintenanceDecision<ProtectedBookRecoveryOutcome> deleteRollbackArtifact(
      ProtectedBookAccess bookAccess, @Nullable Path rollbackArtifactPath) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Path normalizedBookPath = store.normalize(bookAccess.bookFilePath(), "bookFilePath");
    ProtectedBookAccess normalizedAccess =
        new ProtectedBookAccess(normalizedBookPath, bookAccess.passphraseSource());
    ArtifactSelection selection = selectRollbackArtifact(normalizedBookPath, rollbackArtifactPath);
    if (selection instanceof RejectedArtifact rejected) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(rejected.rejection));
    }
    Path selectedRollbackArtifact = ((SelectedArtifact) selection).rollbackArtifactPath;
    return store
        .verifyInitializedBook(normalizedAccess)
        .fold(
            verification -> {
              if (verification
                  instanceof
                  ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
                return MaintenanceDecision.accepted(
                    new ProtectedBookRecoveryOutcome.Rejected(
                        verificationFailed(
                            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, verificationFailure)));
              }
              return deleteVerifiedRollbackArtifact(
                  normalizedBookPath, normalizedAccess, selectedRollbackArtifact);
            },
            MaintenanceDecision::failed);
  }

  /** Restores the selected verified rollback artifact over the live book path. */
  public MaintenanceDecision<ProtectedBookRecoveryOutcome> restoreRollbackArtifact(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      ProtectedBookPassphraseSource expectedPassphraseSource) {
    Path normalizedBookPath = store.normalize(bookFilePath, "bookFilePath");
    ArtifactSelection selection = selectRollbackArtifact(normalizedBookPath, rollbackArtifactPath);
    if (selection instanceof RejectedArtifact rejected) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(rejected.rejection));
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
    Path selectedRollbackArtifact = ((SelectedArtifact) selection).rollbackArtifactPath;
    ProtectedBookAccess rollbackAccess =
        new ProtectedBookAccess(selectedRollbackArtifact, expectedPassphraseSource);
    return store
        .verifyInitializedBook(rollbackAccess)
        .fold(
            verification -> {
              if (verification
                  instanceof
                  ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
                return MaintenanceDecision.accepted(
                    new ProtectedBookRecoveryOutcome.Rejected(
                        verificationFailed(
                            ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
                            verificationFailure)));
              }
              return restoreVerifiedRollbackArtifact(
                  normalizedBookPath, rollbackAccess, selectedRollbackArtifact);
            },
            MaintenanceDecision::failed);
  }

  private MaintenanceDecision<ProtectedBookRestoreOutcome> restoreVerifiedBook(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath) {
    ProtectedBookMaintenanceStore.LeaseAcquisition liveBookLeaseAcquisition =
        store.acquireManagedArtifactLease(normalizedBookPath);
    if (liveBookLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRestoreOutcome.Rejected(
              busyArtifact(
                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, leaseBusy.artifactPath())));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition backupSourceLeaseAcquisition =
        store.acquireExistingArtifactLease(normalizedBackupFilePath);
    if (backupSourceLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      try (ProtectedBookMaintenanceStore.HeldLease ignored =
          (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition) {
        return MaintenanceDecision.accepted(
            new ProtectedBookRestoreOutcome.Rejected(
                busyArtifact(
                    ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, leaseBusy.artifactPath())));
      }
    }
    try (ProtectedBookMaintenanceStore.HeldLease ignoredLiveBook =
            (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition;
        ProtectedBookMaintenanceStore.HeldLease ignoredBackupSource =
            (ProtectedBookMaintenanceStore.HeldLease) backupSourceLeaseAcquisition;
        ProtectedBookMaintenanceStore.StagedBookReplacement stagedReplacement =
            store.stageReplacement(normalizedBackupFilePath, normalizedBookPath)) {
      ProtectedBookAccess stagedAccess =
          new ProtectedBookAccess(
              stagedReplacement.stagedBookPath(),
              new ProtectedBookPassphraseSource.KeyFile(normalizedBackupBookKeyFilePath));
      return store
          .verifyInitializedBook(stagedAccess)
          .fold(
              stagedVerification -> {
                if (stagedVerification
                    instanceof ProtectedBookMaintenanceStore.VerificationFailure stagedFailure) {
                  return MaintenanceDecision.accepted(
                      new ProtectedBookRestoreOutcome.Rejected(
                          verificationFailed(
                              ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                              stagedFailure)));
                }
                return store
                    .appendMaintenanceAudit(
                        stagedAccess,
                        clock.instant(),
                        ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED)
                    .fold(
                        ignoredAudit -> {
                          stagedReplacement.commit();
                          return MaintenanceDecision.accepted(
                              new ProtectedBookRestoreOutcome.Restored(
                                  normalizedBookPath,
                                  normalizedBackupFilePath,
                                  normalizedBackupBookKeyFilePath));
                        },
                        MaintenanceDecision::failed);
              },
              MaintenanceDecision::failed);
    }
  }

  private MaintenanceDecision<ProtectedBookRecoveryOutcome> restoreVerifiedRollbackArtifact(
      Path normalizedBookPath, ProtectedBookAccess rollbackAccess, Path selectedRollbackArtifact) {
    ProtectedBookMaintenanceStore.LeaseAcquisition liveBookLeaseAcquisition =
        store.acquireManagedArtifactLease(normalizedBookPath);
    if (liveBookLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(
              busyArtifact(
                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, leaseBusy.artifactPath())));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition rollbackLeaseAcquisition =
        store.acquireExistingArtifactLease(selectedRollbackArtifact);
    if (rollbackLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      try (ProtectedBookMaintenanceStore.HeldLease ignored =
          (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition) {
        return MaintenanceDecision.accepted(
            new ProtectedBookRecoveryOutcome.Rejected(
                busyArtifact(
                    ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
                    leaseBusy.artifactPath())));
      }
    }
    try (ProtectedBookMaintenanceStore.HeldLease ignoredLiveBook =
            (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition;
        ProtectedBookMaintenanceStore.HeldLease ignoredRollbackArtifact =
            (ProtectedBookMaintenanceStore.HeldLease) rollbackLeaseAcquisition;
        ProtectedBookMaintenanceStore.StagedBookReplacement stagedReplacement =
            store.stageReplacement(selectedRollbackArtifact, normalizedBookPath)) {
      ProtectedBookAccess stagedAccess =
          new ProtectedBookAccess(
              stagedReplacement.stagedBookPath(), rollbackAccess.passphraseSource());
      return store
          .verifyInitializedBook(stagedAccess)
          .fold(
              stagedVerification -> {
                if (stagedVerification
                    instanceof ProtectedBookMaintenanceStore.VerificationFailure stagedFailure) {
                  return MaintenanceDecision.accepted(
                      new ProtectedBookRecoveryOutcome.Rejected(
                          verificationFailed(
                              ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                              stagedFailure)));
                }
                return store
                    .appendMaintenanceAudit(
                        stagedAccess,
                        clock.instant(),
                        ProtectedBookMaintenanceAuditKind.REKEY_ROLLBACK_RESTORED)
                    .fold(
                        ignoredAudit -> {
                          stagedReplacement.commit();
                          return MaintenanceDecision.accepted(
                              new ProtectedBookRecoveryOutcome.Restored(
                                  normalizedBookPath, selectedRollbackArtifact));
                        },
                        MaintenanceDecision::failed);
              },
              MaintenanceDecision::failed);
    }
  }

  private MaintenanceDecision<ProtectedBookRecoveryOutcome> deleteVerifiedRollbackArtifact(
      Path normalizedBookPath, ProtectedBookAccess liveBookAccess, Path selectedRollbackArtifact) {
    ProtectedBookMaintenanceStore.LeaseAcquisition liveBookLeaseAcquisition =
        store.acquireExistingArtifactLease(normalizedBookPath);
    if (liveBookLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      return MaintenanceDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(
              busyArtifact(
                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, leaseBusy.artifactPath())));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition rollbackLeaseAcquisition =
        store.acquireExistingArtifactLease(selectedRollbackArtifact);
    if (rollbackLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      try (ProtectedBookMaintenanceStore.HeldLease ignored =
          (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition) {
        return MaintenanceDecision.accepted(
            new ProtectedBookRecoveryOutcome.Rejected(
                busyArtifact(
                    ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
                    leaseBusy.artifactPath())));
      }
    }
    try (ProtectedBookMaintenanceStore.HeldLease ignoredLiveBook =
            (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition;
        ProtectedBookMaintenanceStore.HeldLease ignoredRollbackArtifact =
            (ProtectedBookMaintenanceStore.HeldLease) rollbackLeaseAcquisition;
        ProtectedBookMaintenanceStore.StagedRollbackArtifactDeletion stagedDeletion =
            store.stageRollbackArtifactDeletion(selectedRollbackArtifact)) {
      Instant recordedAt = clock.instant();
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

  private MaintenanceDecision<ProtectedBookBackupOutcome> commitBackedUpPair(
      ProtectedBookAccess liveBookAccess,
      Instant recordedAt,
      ProtectedBookMaintenanceStore.StagedBackupPair stagedBackupPair,
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath) {
    try {
      stagedBackupPair.commit();
      return MaintenanceDecision.accepted(
          new ProtectedBookBackupOutcome.BackedUp(
              normalizedBookPath, normalizedBackupFilePath, normalizedBackupBookKeyFilePath));
    } catch (RuntimeException commitFailure) {
      return compensateAuditAfterExternalCommitFailure(
          liveBookAccess,
          recordedAt,
          ProtectedBookMaintenanceAuditCompensationKind.BACKUP_CREATED,
          "Failed to publish the staged FinGrind backup pair.",
          "backupFilePath",
          commitFailure);
    }
  }

  private MaintenanceDecision<ProtectedBookRecoveryOutcome> commitDeletedRollbackArtifact(
      Path normalizedBookPath,
      ProtectedBookAccess liveBookAccess,
      Instant recordedAt,
      Path selectedRollbackArtifact,
      ProtectedBookMaintenanceStore.StagedRollbackArtifactDeletion stagedDeletion) {
    try {
      stagedDeletion.commit();
      return MaintenanceDecision.accepted(
          new ProtectedBookRecoveryOutcome.Deleted(normalizedBookPath, selectedRollbackArtifact));
    } catch (RuntimeException commitFailure) {
      return compensateAuditAfterExternalCommitFailure(
          liveBookAccess,
          recordedAt,
          ProtectedBookMaintenanceAuditCompensationKind.REKEY_ROLLBACK_DELETED,
          "Failed to delete the staged FinGrind rollback artifact.",
          "rollbackArtifactPath",
          commitFailure);
    }
  }

  private <T> MaintenanceDecision<T> compensateAuditAfterExternalCommitFailure(
      ProtectedBookAccess liveBookAccess,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditCompensationKind auditKind,
      String failureMessage,
      String argumentName,
      RuntimeException commitFailure) {
    return store
        .appendMaintenanceAuditCompensation(liveBookAccess, recordedAt, auditKind)
        .fold(
            ignoredCompletion ->
                MaintenanceDecision.failed(
                    new MaintenanceFailure(
                        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
                        failureMessage,
                        commitFailure.getMessage(),
                        argumentName)),
            retractFailure -> MaintenanceDecision.failed(retractFailure));
  }

  private ArtifactSelection selectRollbackArtifact(
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

  private ProtectedBookMaintenanceRejection.ArtifactVerificationFailed verificationFailed(
      ProtectedBookMaintenanceArtifactRole artifactRole,
      ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
    return new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
        artifactRole, verificationFailure.artifactPath(), verificationFailure.failure());
  }

  private ProtectedBookMaintenanceRejection.ArtifactBusy busyArtifact(
      ProtectedBookMaintenanceArtifactRole artifactRole, Path artifactPath) {
    return new ProtectedBookMaintenanceRejection.ArtifactBusy(artifactRole, artifactPath);
  }

  /**
   * Local rollback-artifact selection result before projection into public maintenance outcomes.
   */
  private sealed interface ArtifactSelection permits SelectedArtifact, RejectedArtifact {}

  /** Local selected rollback artifact that passed existence and sibling checks. */
  private static final class SelectedArtifact implements ArtifactSelection {
    private final Path rollbackArtifactPath;

    private SelectedArtifact(Path rollbackArtifactPath) {
      this.rollbackArtifactPath =
          Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Local rejected rollback-artifact selection carrying one deterministic refusal. */
  private static final class RejectedArtifact implements ArtifactSelection {
    private final ProtectedBookMaintenanceRejection rejection;

    private RejectedArtifact(ProtectedBookMaintenanceRejection rejection) {
      this.rejection = Objects.requireNonNull(rejection, "rejection");
    }
  }
}
