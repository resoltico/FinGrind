package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceWorkflow;
import dev.erst.fingrind.executor.maintenance.ProtectedBookPassphraseSource;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Thin published-language adapter over the local protected-book maintenance workflow. */
public final class ProtectedBookMaintenanceService {
  private final ProtectedBookMaintenanceWorkflow workflow;

  /** Creates the protected-book maintenance service with one local workflow and one store seam. */
  public ProtectedBookMaintenanceService(Clock clock, ProtectedBookMaintenanceStore store) {
    this.workflow =
        new ProtectedBookMaintenanceWorkflow(
            Objects.requireNonNull(clock, "clock"), Objects.requireNonNull(store, "store"));
  }

  /** Exports one closed encrypted-book backup pair from one initialized FinGrind book. */
  public ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
    return toPublishedBackup(
        workflow.backupBook(
            ProtectedBookAccess.fromPublished(bookAccess), backupFilePath, backupBookKeyFilePath));
  }

  /** Restores one verified encrypted-book backup pair onto one live FinGrind book path. */
  public ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath, Path bookKeyFilePath, Path backupFilePath, Path backupKeyFilePath) {
    return toPublishedRestore(
        workflow.restoreBook(bookFilePath, bookKeyFilePath, backupFilePath, backupKeyFilePath));
  }

  /** Inspects stale sibling rollback artifacts for the selected book path without side effects. */
  public ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath) {
    return toPublishedRecovery(workflow.inspectRollbackArtifacts(bookFilePath));
  }

  /** Deletes one selected sibling rollback artifact for the selected initialized book. */
  public ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
      BookAccess bookAccess, @Nullable Path rollbackArtifactPath) {
    return toPublishedRecovery(
        workflow.deleteRollbackArtifact(
            ProtectedBookAccess.fromPublished(bookAccess), rollbackArtifactPath));
  }

  /** Restores one selected sibling rollback artifact for the selected book path. */
  public ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      BookAccess.PassphraseSource expectedPassphraseSource) {
    return toPublishedRecovery(
        workflow.restoreRollbackArtifact(
            bookFilePath,
            rollbackArtifactPath,
            ProtectedBookPassphraseSource.fromPublished(
                Objects.requireNonNull(expectedPassphraseSource, "expectedPassphraseSource"))));
  }

  private static ContractDecision<BackupBookResult> toPublishedBackup(
      MaintenanceDecision<dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome>
          decision) {
    return decision.fold(
        outcome ->
            ContractDecision.accepted(
                ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(outcome)),
        failure -> ContractDecision.rejected(failure.toContractFailure()));
  }

  private static ContractDecision<RestoreBookResult> toPublishedRestore(
      MaintenanceDecision<dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome>
          decision) {
    return decision.fold(
        outcome ->
            ContractDecision.accepted(
                ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(outcome)),
        failure -> ContractDecision.rejected(failure.toContractFailure()));
  }

  private static ContractDecision<RekeyRollbackResult> toPublishedRecovery(
      MaintenanceDecision<dev.erst.fingrind.executor.maintenance.ProtectedBookRecoveryOutcome>
          decision) {
    return decision.fold(
        outcome ->
            ContractDecision.accepted(
                ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(outcome)),
        failure -> ContractDecision.rejected(failure.toContractFailure()));
  }
}
