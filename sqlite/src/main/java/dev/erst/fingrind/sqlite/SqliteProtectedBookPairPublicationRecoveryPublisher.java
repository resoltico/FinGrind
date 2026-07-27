package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.util.Objects;

/** Reconciles one exact durable pair-publication operation without widening its authority. */
final class SqliteProtectedBookPairPublicationRecoveryPublisher {
  private final SqlitePairPublicationRecoveryWorkflow workflow;

  SqliteProtectedBookPairPublicationRecoveryPublisher(
      SqliteProtectedBookPairPublicationPreparation.RecoveredPairVerifier recoveredPairVerifier,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer) {
    this.workflow =
        new SqlitePairPublicationRecoveryWorkflow(
            Objects.requireNonNull(recoveredPairVerifier, "recoveredPairVerifier"),
            Objects.requireNonNull(directoryForcer, "directoryForcer"),
            Objects.requireNonNull(recoveryRecordFileForcer, "recoveryRecordFileForcer"));
  }

  SqlitePairPublicationReconciliation recover(
      SqliteProtectedBookPairPublicationRecord record,
      RestoredBookTargetPolicy expectedBookTargetPolicy,
      ProtectedBookPairPublicationRecoveryRequest request,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      boolean incompleteEvidence) {
    return workflow.recover(
        record,
        expectedBookTargetPolicy,
        request,
        bookArtifactRole,
        secretArtifactRole,
        incompleteEvidence);
  }
}
