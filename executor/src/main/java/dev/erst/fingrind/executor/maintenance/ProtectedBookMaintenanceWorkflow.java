package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Public maintenance façade over focused backup, restore, and recovery workflow owners. */
public final class ProtectedBookMaintenanceWorkflow {
  private final ProtectedBookBackupWorkflow backupWorkflow;
  private final ProtectedBookRestoreWorkflow restoreWorkflow;
  private final ProtectedBookRecoveryWorkflow recoveryWorkflow;

  /** Creates one maintenance workflow over the clock and protected-book store seams. */
  public ProtectedBookMaintenanceWorkflow(Clock clock, ProtectedBookMaintenanceStore store) {
    ProtectedBookMaintenanceWorkflowSupport support =
        new ProtectedBookMaintenanceWorkflowSupport(
            Objects.requireNonNull(clock, "clock"), Objects.requireNonNull(store, "store"));
    this.backupWorkflow = new ProtectedBookBackupWorkflow(support);
    this.restoreWorkflow = new ProtectedBookRestoreWorkflow(support);
    this.recoveryWorkflow = new ProtectedBookRecoveryWorkflow(support);
  }

  /** Exports one verified encrypted backup pair for the selected live book. */
  public MaintenanceDecision<ProtectedBookBackupOutcome> backupBook(
      ProtectedBookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
    return backupWorkflow.backupBook(bookAccess, backupFilePath, backupBookKeyFilePath);
  }

  /** Restores one verified encrypted backup pair over the selected live book path. */
  public MaintenanceDecision<ProtectedBookRestoreOutcome> restoreBook(
      Path bookFilePath, Path bookKeyFilePath, Path backupFilePath, Path backupKeyFilePath) {
    return restoreWorkflow.restoreBook(
        bookFilePath, bookKeyFilePath, backupFilePath, backupKeyFilePath);
  }

  /** Lists every sibling rollback artifact for the selected live book without mutating state. */
  public MaintenanceDecision<ProtectedBookRecoveryOutcome> inspectRollbackArtifacts(
      Path bookFilePath) {
    return recoveryWorkflow.inspectRollbackArtifacts(bookFilePath);
  }

  /** Deletes one verified rollback artifact for the selected live book. */
  public MaintenanceDecision<ProtectedBookRecoveryOutcome> deleteRollbackArtifact(
      ProtectedBookAccess bookAccess, @Nullable Path rollbackArtifactPath) {
    return recoveryWorkflow.deleteRollbackArtifact(bookAccess, rollbackArtifactPath);
  }

  /** Restores the selected verified rollback artifact over the live book path. */
  public MaintenanceDecision<ProtectedBookRecoveryOutcome> restoreRollbackArtifact(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      ProtectedBookPassphraseSource expectedPassphraseSource) {
    return recoveryWorkflow.restoreRollbackArtifact(
        bookFilePath, rollbackArtifactPath, expectedPassphraseSource);
  }
}
