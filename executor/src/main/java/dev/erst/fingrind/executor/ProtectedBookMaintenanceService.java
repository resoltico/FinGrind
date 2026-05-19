package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRecoveryOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceEvent;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Application service that owns protected-book maintenance orchestration and refusals. */
public final class ProtectedBookMaintenanceService {
  private final Clock clock;
  private final ProtectedBookMaintenanceStore store;

  /** Creates the protected-book maintenance service with one narrow store seam. */
  public ProtectedBookMaintenanceService(Clock clock, ProtectedBookMaintenanceStore store) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.store = Objects.requireNonNull(store, "store");
  }

  /** Exports one closed encrypted-book backup pair from one initialized FinGrind book. */
  public ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
    return backupBookLocal(bookAccess, backupFilePath, backupBookKeyFilePath)
        .fold(
            outcome ->
                ContractDecision.accepted(
                    ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(outcome)),
            ContractDecision::rejected);
  }

  /** Restores one verified encrypted-book backup pair onto one live FinGrind book path. */
  public ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    return restoreBookLocal(bookFilePath, backupFilePath, backupBookKeyFilePath)
        .fold(
            outcome ->
                ContractDecision.accepted(
                    ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(outcome)),
            ContractDecision::rejected);
  }

  /** Inspects stale sibling rollback artifacts for the selected book path. */
  public ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath) {
    return inspectRollbackArtifacts(store.normalize(bookFilePath, "bookFilePath"))
        .fold(
            outcome ->
                ContractDecision.accepted(
                    ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(outcome)),
            ContractDecision::rejected);
  }

  /** Deletes one selected sibling rollback artifact for the selected book path. */
  public ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
      Path bookFilePath, @Nullable Path rollbackArtifactPath) {
    Path normalizedBookPath = store.normalize(bookFilePath, "bookFilePath");
    return deleteRollbackArtifact(
            normalizedBookPath, selectRollbackArtifact(normalizedBookPath, rollbackArtifactPath))
        .fold(
            outcome ->
                ContractDecision.accepted(
                    ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(outcome)),
            ContractDecision::rejected);
  }

  /** Restores one selected sibling rollback artifact for the selected book path. */
  public ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      PassphraseSource expectedPassphraseSource) {
    Path normalizedBookPath = store.normalize(bookFilePath, "bookFilePath");
    return restoreRollbackArtifact(
            normalizedBookPath,
            selectRollbackArtifact(normalizedBookPath, rollbackArtifactPath),
            Objects.requireNonNull(
                expectedPassphraseSource,
                "expectedPassphraseSource is required for "
                    + OperationId.RESTORE_REKEY_ROLLBACK.wireName()
                    + "."))
        .fold(
            outcome ->
                ContractDecision.accepted(
                    ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(outcome)),
            ContractDecision::rejected);
  }

  private ContractDecision<ProtectedBookBackupOutcome> backupBookLocal(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Path normalizedBookPath = store.normalize(bookAccess.bookFilePath(), "bookFilePath");
    BookAccess normalizedAccess = new BookAccess(normalizedBookPath, bookAccess.passphraseSource());
    Path normalizedBackupFilePath = store.normalize(backupFilePath, "backupFilePath");
    Path normalizedBackupBookKeyFilePath =
        store.normalize(backupBookKeyFilePath, "backupBookKeyFilePath");
    List<Path> blockingArtifacts = store.blockingArtifactsForBook(normalizedBookPath);
    if (!blockingArtifacts.isEmpty()) {
      return ContractDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                  normalizedBookPath, blockingArtifacts)));
    }
    if (Files.exists(normalizedBackupFilePath, LinkOption.NOFOLLOW_LINKS)) {
      return ContractDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(
                  normalizedBackupFilePath)));
    }
    if (Files.exists(normalizedBackupBookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
      return ContractDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BackupKeyFileAlreadyExists(
                  normalizedBackupBookKeyFilePath)));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition leaseAcquisition =
        store.acquireExclusiveLease(normalizedBookPath);
    if (leaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      return ContractDecision.accepted(
          new ProtectedBookBackupOutcome.Rejected(
              busyArtifact(
                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, leaseBusy.artifactPath())));
    }
    try (ProtectedBookMaintenanceStore.HeldLease ignored =
        (ProtectedBookMaintenanceStore.HeldLease) leaseAcquisition) {
      return store
          .verifyInitializedBook(normalizedAccess)
          .fold(
              verification -> {
                if (verification
                    instanceof
                    ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
                  return ContractDecision.accepted(
                      new ProtectedBookBackupOutcome.Rejected(
                          verificationFailed(
                              ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                              verificationFailure)));
                }
                return store
                    .publishBackupPair(
                        normalizedAccess, normalizedBackupFilePath, normalizedBackupBookKeyFilePath)
                    .fold(
                        publishedBackup -> {
                          store.recordMaintenanceEvent(
                              ProtectedBookMaintenanceEvent.backupCreated(
                                  clock.instant(),
                                  normalizedBookPath,
                                  normalizedBackupFilePath,
                                  normalizedBackupBookKeyFilePath));
                          return ContractDecision.accepted(
                              new ProtectedBookBackupOutcome.BackedUp(
                                  normalizedBookPath,
                                  normalizedBackupFilePath,
                                  normalizedBackupBookKeyFilePath));
                        },
                        ContractDecision::rejected);
              },
              ContractDecision::rejected);
    }
  }

  private ContractDecision<ProtectedBookRestoreOutcome> restoreBookLocal(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    Path normalizedBookPath = store.normalize(bookFilePath, "bookFilePath");
    Path normalizedBackupFilePath = store.normalize(backupFilePath, "backupFilePath");
    Path normalizedBackupBookKeyFilePath =
        store.normalize(backupBookKeyFilePath, "backupBookKeyFilePath");
    List<Path> liveBookBlockingArtifacts = store.blockingArtifactsForBook(normalizedBookPath);
    if (!liveBookBlockingArtifacts.isEmpty()) {
      return ContractDecision.accepted(
          new ProtectedBookRestoreOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                  normalizedBookPath, liveBookBlockingArtifacts)));
    }
    List<Path> backupBlockingArtifacts =
        store.blockingArtifactsForBackupSource(normalizedBackupFilePath);
    if (!backupBlockingArtifacts.isEmpty()) {
      return ContractDecision.accepted(
          new ProtectedBookRestoreOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                  normalizedBackupFilePath, backupBlockingArtifacts)));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition liveBookLeaseAcquisition =
        store.acquireExclusiveLease(normalizedBookPath);
    if (liveBookLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      return ContractDecision.accepted(
          new ProtectedBookRestoreOutcome.Rejected(
              busyArtifact(
                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, leaseBusy.artifactPath())));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition backupSourceLeaseAcquisition =
        store.acquireExclusiveLease(normalizedBackupFilePath);
    if (backupSourceLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      try (ProtectedBookMaintenanceStore.HeldLease ignored =
          (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition) {
        return ContractDecision.accepted(
            new ProtectedBookRestoreOutcome.Rejected(
                busyArtifact(
                    ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, leaseBusy.artifactPath())));
      }
    }
    BookAccess backupAccess =
        new BookAccess(
            normalizedBackupFilePath,
            new BookAccess.PassphraseSource.KeyFile(normalizedBackupBookKeyFilePath));
    try (ProtectedBookMaintenanceStore.HeldLease ignoredLiveBook =
            (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition;
        ProtectedBookMaintenanceStore.HeldLease ignoredBackupSource =
            (ProtectedBookMaintenanceStore.HeldLease) backupSourceLeaseAcquisition) {
      return store
          .verifyInitializedBook(backupAccess)
          .fold(
              verification -> {
                if (verification
                    instanceof
                    ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
                  return ContractDecision.accepted(
                      new ProtectedBookRestoreOutcome.Rejected(
                          verificationFailed(
                              ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
                              verificationFailure)));
                }
                try (ProtectedBookMaintenanceStore.PreparedBookReplacement replacement =
                    store.prepareReplacement(normalizedBackupFilePath, normalizedBookPath)) {
                  BookAccess restoredAccess =
                      new BookAccess(
                          replacement.targetBookPath(),
                          new BookAccess.PassphraseSource.KeyFile(normalizedBackupBookKeyFilePath));
                  return store
                      .verifyInitializedBook(restoredAccess)
                      .fold(
                          restoredVerification -> {
                            if (restoredVerification
                                instanceof
                                ProtectedBookMaintenanceStore.VerificationFailure
                                    restoredVerificationFailure) {
                              replacement.rollback();
                              return ContractDecision.accepted(
                                  new ProtectedBookRestoreOutcome.Rejected(
                                      verificationFailed(
                                          ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                                          restoredVerificationFailure)));
                            }
                            replacement.commit();
                            store.recordMaintenanceEvent(
                                ProtectedBookMaintenanceEvent.backupRestored(
                                    clock.instant(),
                                    normalizedBookPath,
                                    normalizedBackupFilePath,
                                    normalizedBackupBookKeyFilePath));
                            return ContractDecision.accepted(
                                new ProtectedBookRestoreOutcome.Restored(
                                    normalizedBookPath,
                                    normalizedBackupFilePath,
                                    normalizedBackupBookKeyFilePath));
                          },
                          ContractDecision::rejected);
                }
              },
              ContractDecision::rejected);
    }
  }

  private ContractDecision<ProtectedBookRecoveryOutcome> inspectRollbackArtifacts(
      Path normalizedBookPath) {
    List<Path> rollbackArtifacts = store.staleRollbackArtifacts(normalizedBookPath);
    store.recordMaintenanceEvent(
        ProtectedBookMaintenanceEvent.rollbackArtifactsInspected(
            clock.instant(), normalizedBookPath, rollbackArtifacts));
    return ContractDecision.accepted(
        new ProtectedBookRecoveryOutcome.Inspected(normalizedBookPath, rollbackArtifacts));
  }

  private ContractDecision<ProtectedBookRecoveryOutcome> restoreRollbackArtifact(
      Path normalizedBookPath,
      ArtifactSelection selection,
      PassphraseSource expectedPassphraseSource) {
    if (selection instanceof ArtifactSelection.Rejected rejected) {
      return ContractDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(rejected.rejection()));
    }
    List<Path> liveBookBlockingArtifacts =
        store.blockingArtifactsForBook(normalizedBookPath).stream()
            .filter(path -> !store.isRollbackArtifactForBook(normalizedBookPath, path))
            .toList();
    if (!liveBookBlockingArtifacts.isEmpty()) {
      return ContractDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(
              new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                  normalizedBookPath, liveBookBlockingArtifacts)));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition liveBookLeaseAcquisition =
        store.acquireExclusiveLease(normalizedBookPath);
    if (liveBookLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      return ContractDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(
              busyArtifact(
                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, leaseBusy.artifactPath())));
    }
    Path selectedRollbackArtifact = ((ArtifactSelection.Selected) selection).rollbackArtifactPath();
    ProtectedBookMaintenanceStore.LeaseAcquisition rollbackLeaseAcquisition =
        store.acquireExclusiveLease(selectedRollbackArtifact);
    if (rollbackLeaseAcquisition instanceof ProtectedBookMaintenanceStore.LeaseBusy leaseBusy) {
      try (ProtectedBookMaintenanceStore.HeldLease ignored =
          (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition) {
        return ContractDecision.accepted(
            new ProtectedBookRecoveryOutcome.Rejected(
                busyArtifact(
                    ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
                    leaseBusy.artifactPath())));
      }
    }
    BookAccess rollbackAccess = new BookAccess(selectedRollbackArtifact, expectedPassphraseSource);
    try (ProtectedBookMaintenanceStore.HeldLease ignoredLiveBook =
            (ProtectedBookMaintenanceStore.HeldLease) liveBookLeaseAcquisition;
        ProtectedBookMaintenanceStore.HeldLease ignoredRollbackArtifact =
            (ProtectedBookMaintenanceStore.HeldLease) rollbackLeaseAcquisition) {
      return store
          .verifyInitializedBook(rollbackAccess)
          .fold(
              verification -> {
                if (verification
                    instanceof
                    ProtectedBookMaintenanceStore.VerificationFailure verificationFailure) {
                  return ContractDecision.accepted(
                      new ProtectedBookRecoveryOutcome.Rejected(
                          verificationFailed(
                              ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
                              verificationFailure)));
                }
                try (ProtectedBookMaintenanceStore.PreparedBookReplacement replacement =
                    store.prepareReplacement(selectedRollbackArtifact, normalizedBookPath)) {
                  BookAccess restoredAccess =
                      new BookAccess(replacement.targetBookPath(), expectedPassphraseSource);
                  return store
                      .verifyInitializedBook(restoredAccess)
                      .fold(
                          restoredVerification -> {
                            if (restoredVerification
                                instanceof
                                ProtectedBookMaintenanceStore.VerificationFailure
                                    restoredVerificationFailure) {
                              replacement.rollback();
                              return ContractDecision.accepted(
                                  new ProtectedBookRecoveryOutcome.Rejected(
                                      verificationFailed(
                                          ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                                          restoredVerificationFailure)));
                            }
                            replacement.commit();
                            store.recordMaintenanceEvent(
                                ProtectedBookMaintenanceEvent.rollbackArtifactRestored(
                                    clock.instant(), normalizedBookPath, selectedRollbackArtifact));
                            return ContractDecision.accepted(
                                new ProtectedBookRecoveryOutcome.Restored(
                                    normalizedBookPath, selectedRollbackArtifact));
                          },
                          ContractDecision::rejected);
                }
              },
              ContractDecision::rejected);
    }
  }

  private ContractDecision<ProtectedBookRecoveryOutcome> deleteRollbackArtifact(
      Path normalizedBookPath, ArtifactSelection selection) {
    if (selection instanceof ArtifactSelection.Rejected rejected) {
      return ContractDecision.accepted(
          new ProtectedBookRecoveryOutcome.Rejected(rejected.rejection()));
    }
    Path selectedRollbackArtifact = ((ArtifactSelection.Selected) selection).rollbackArtifactPath();
    store.deleteRollbackArtifact(selectedRollbackArtifact);
    store.recordMaintenanceEvent(
        ProtectedBookMaintenanceEvent.rollbackArtifactDeleted(
            clock.instant(), normalizedBookPath, selectedRollbackArtifact));
    return ContractDecision.accepted(
        new ProtectedBookRecoveryOutcome.Deleted(normalizedBookPath, selectedRollbackArtifact));
  }

  private ArtifactSelection selectRollbackArtifact(
      Path normalizedBookPath, @Nullable Path rollbackArtifactPath) {
    List<Path> rollbackArtifacts = store.staleRollbackArtifacts(normalizedBookPath);
    if (rollbackArtifactPath == null) {
      if (rollbackArtifacts.isEmpty()) {
        return new ArtifactSelection.Rejected(
            new ProtectedBookMaintenanceRejection.NoRollbackArtifactsFound(normalizedBookPath));
      }
      if (rollbackArtifacts.size() > 1) {
        return new ArtifactSelection.Rejected(
            new ProtectedBookMaintenanceRejection.RollbackArtifactSelectionRequired(
                normalizedBookPath, rollbackArtifacts));
      }
      return new ArtifactSelection.Selected(rollbackArtifacts.getFirst());
    }
    Path normalizedRollbackArtifactPath =
        store.normalize(rollbackArtifactPath, "rollbackArtifactPath");
    if (!Files.exists(normalizedRollbackArtifactPath, LinkOption.NOFOLLOW_LINKS)) {
      return new ArtifactSelection.Rejected(
          new ProtectedBookMaintenanceRejection.RollbackArtifactNotFound(
              normalizedRollbackArtifactPath));
    }
    if (!store.isRollbackArtifactForBook(normalizedBookPath, normalizedRollbackArtifactPath)) {
      return new ArtifactSelection.Rejected(
          new ProtectedBookMaintenanceRejection.RollbackArtifactNotForBook(
              normalizedBookPath, normalizedRollbackArtifactPath));
    }
    return new ArtifactSelection.Selected(normalizedRollbackArtifactPath);
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
  private sealed interface ArtifactSelection
      permits ArtifactSelection.Selected, ArtifactSelection.Rejected {
    record Selected(Path rollbackArtifactPath) implements ArtifactSelection {}

    record Rejected(ProtectedBookMaintenanceRejection rejection) implements ArtifactSelection {}
  }
}
