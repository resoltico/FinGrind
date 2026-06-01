package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
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
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    ProtectedBookMaintenanceStore store = support.store();
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
    return support.continueWithVerifiedBook(
        backupAccess,
        ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
        ignoredVerified ->
            restoreVerifiedBook(
                normalizedBookPath, normalizedBackupFilePath, normalizedBackupBookKeyFilePath),
        ProtectedBookRestoreOutcome.Rejected::new);
  }

  private MaintenanceDecision<ProtectedBookRestoreOutcome> restoreVerifiedBook(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath) {
    return support.restoreVerifiedSourceArtifact(
        normalizedBookPath,
        normalizedBackupFilePath,
        new ProtectedBookPassphraseSource.KeyFile(normalizedBackupBookKeyFilePath),
        ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
        ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED,
        ProtectedBookRestoreOutcome.Rejected::new,
        () ->
            new ProtectedBookRestoreOutcome.Restored(
                normalizedBookPath, normalizedBackupFilePath, normalizedBackupBookKeyFilePath));
  }
}
