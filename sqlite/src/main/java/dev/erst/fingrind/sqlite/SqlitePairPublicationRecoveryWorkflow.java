package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.util.Objects;

/** Drives exact-record recovery after admission has selected a single pair publication. */
final class SqlitePairPublicationRecoveryWorkflow {
  private final SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer;
  private final SqlitePairPublicationMemberReconciler memberReconciler;
  private final SqlitePairPublicationRecoveryProof recoveryProof;

  SqlitePairPublicationRecoveryWorkflow(
      SqliteProtectedBookPairPublicationPreparation.RecoveredPairVerifier recoveredPairVerifier,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer) {
    this.directoryForcer = Objects.requireNonNull(directoryForcer, "directoryForcer");
    this.memberReconciler =
        new SqlitePairPublicationMemberReconciler(directoryForcer, recoveryRecordFileForcer);
    this.recoveryProof =
        new SqlitePairPublicationRecoveryProof(recoveredPairVerifier, directoryForcer);
  }

  SqlitePairPublicationReconciliation recover(
      SqliteProtectedBookPairPublicationRecord record,
      RestoredBookTargetPolicy expectedBookTargetPolicy,
      ProtectedBookPairPublicationRecoveryRequest request,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      boolean incompleteEvidence) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    RestoredBookTargetPolicy checkedPolicy =
        Objects.requireNonNull(expectedBookTargetPolicy, "expectedBookTargetPolicy");
    ProtectedBookPairPublicationRecoveryRequest checkedRequest =
        Objects.requireNonNull(request, "request");
    if (SqlitePairPublicationEvidenceState.isDurablyCompleted(checkedRecord)) {
      return completedPublicationResult(checkedRecord, checkedRequest);
    }
    SqlitePairPublicationReconciliation repaired =
        repairIncompleteEvidenceIfRequired(
            checkedRecord, checkedPolicy, checkedRequest, incompleteEvidence);
    if (repaired != null) {
      return repaired;
    }
    if (checkedRecord.bookTargetPolicy != checkedPolicy
        || !checkedRecord.binding.matches(checkedRequest)) {
      return mismatch(checkedRecord);
    }
    SqlitePairPublicationReconciliation retained = retainedPrepublicationResult(checkedRecord);
    if (retained != null) {
      return retained;
    }
    if (checkedRecord.finalBookMatches() && checkedRecord.finalSecretMatches()) {
      return completeVisibilityResult(checkedRecord);
    }
    return recoverIncompleteVisibility(checkedRecord, bookArtifactRole, secretArtifactRole);
  }

  /**
   * A terminal backup publication remains authoritative for its selected artifact so a later
   * request cannot reinterpret an occupied backup as untracked pair residue. Terminal history from
   * any other operation is inert once its final publication has been acknowledged.
   */
  private static SqlitePairPublicationReconciliation completedPublicationResult(
      SqliteProtectedBookPairPublicationRecord record,
      ProtectedBookPairPublicationRecoveryRequest request) {
    if (record.binding
            instanceof dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding.Backup
        && request instanceof ProtectedBookPairPublicationRecoveryRequest.Backup) {
      return new SqlitePairPublicationReconciliationExistingCompleteBackup(
          record.bookTargetPath, record.secretTargetPath);
    }
    return SqlitePairPublicationReconciliationAbsent.INSTANCE;
  }

  private @org.jspecify.annotations.Nullable SqlitePairPublicationReconciliation
      repairIncompleteEvidenceIfRequired(
          SqliteProtectedBookPairPublicationRecord record,
          RestoredBookTargetPolicy expectedPolicy,
          ProtectedBookPairPublicationRecoveryRequest request,
          boolean incompleteEvidence) {
    if (!incompleteEvidence) {
      return null;
    }
    if (record.bookTargetPolicy != expectedPolicy || !record.binding.matches(request)) {
      throw SqliteProtectedBookPairPublicationRecovery.recoveryPending(record);
    }
    if (!SqliteProtectedBookPairPublicationRecoverySupport.hasOwnedStages(record)
        || !recoveryProof.verifiesRecordBoundPair(record)
        || !recoveryProof.repairsIncompleteEvidence(record)) {
      return SqliteProtectedBookPairPublicationRecovery.evidenceBlocked(record);
    }
    return null;
  }

  private @org.jspecify.annotations.Nullable SqlitePairPublicationReconciliation
      retainedPrepublicationResult(SqliteProtectedBookPairPublicationRecord record) {
    if (!SqliteProtectedBookPairPublicationEvidenceLifecycle.hasDurablyRetainedPrepublication(
        record)) {
      return null;
    }
    if (!record.finalBookMatches() && !record.finalSecretMatches()) {
      return SqlitePairPublicationReconciliationAbsent.INSTANCE;
    }
    return SqliteProtectedBookPairPublicationRecovery.evidenceBlocked(record);
  }

  private SqlitePairPublicationReconciliation completeVisibilityResult(
      SqliteProtectedBookPairPublicationRecord record) {
    if (!SqliteProtectedBookPairPublicationRecoverySupport.hasOwnedStages(record)) {
      return SqliteProtectedBookPairPublicationRecovery.completionUncertain(record);
    }
    var secret =
        SqliteProtectedBookPairPublicationRecoverySupport.forceRecoveredMember(
            directoryForcer,
            SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                .GENERATED_SECRET_PUBLICATION,
            record.secretTargetPath);
    var book =
        SqliteProtectedBookPairPublicationRecoverySupport.forceRecoveredMember(
            directoryForcer,
            SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.BOOK_PUBLICATION,
            record.bookTargetPath);
    if (!secret.isDurable()
        || !book.isDurable()
        || !recoveryProof.verifiesRecordBoundPair(record)) {
      return SqliteProtectedBookPairPublicationRecovery.completionUncertain(
          record, book.state(), secret.state());
    }
    return recovered(record);
  }

  private SqlitePairPublicationReconciliation recoverIncompleteVisibility(
      SqliteProtectedBookPairPublicationRecord record,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    if (!SqliteProtectedBookPairPublicationRecoverySupport.hasOwnedStages(record)
        || !recoveryProof.verifiesRecordBoundPair(record)) {
      return SqliteProtectedBookPairPublicationRecovery.completionUncertain(record);
    }
    SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan bookPlan =
        SqliteProtectedBookPairPublicationRecoverySupport.bookPlan(record);
    SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan secretPlan =
        SqliteProtectedBookPairPublicationRecoverySupport.secretPlan(record);
    if (!bookPlan.canReconcile() || !secretPlan.canReconcile()) {
      return SqliteProtectedBookPairPublicationRecovery.completionUncertain(record);
    }
    return reconcileMembers(record, bookPlan, secretPlan, bookArtifactRole, secretArtifactRole);
  }

  private SqlitePairPublicationReconciliation reconcileMembers(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan bookPlan,
      SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan secretPlan,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    try (SqlitePublicationCapabilityWitness.Set capabilityWitnesses =
        SqlitePairPublicationRecoveryCapabilities.acquire(
            record, bookPlan, secretPlan, bookArtifactRole, secretArtifactRole)) {
      SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation secret =
          memberReconciler.reconcileSecret(record, secretPlan, capabilityWitnesses);
      if (!secret.isDurable()) {
        return SqliteProtectedBookPairPublicationRecovery.completionUncertain(
            record,
            SqliteProtectedBookPairPublicationRecoverySupport.bookState(record),
            secret.state());
      }
      SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation book =
          memberReconciler.reconcileBook(record, bookPlan, capabilityWitnesses);
      if (!book.isDurable() || !recoveryProof.verifiesRecordBoundPair(record)) {
        return SqliteProtectedBookPairPublicationRecovery.completionUncertain(
            record, book.state(), secret.state());
      }
      return recovered(record);
    }
  }

  private SqlitePairPublicationReconciliation mismatch(
      SqliteProtectedBookPairPublicationRecord record) {
    if (!record.finalBookMatches() || !record.finalSecretMatches()) {
      throw SqliteProtectedBookPairPublicationRecovery.recoveryPending(record);
    }
    return SqliteProtectedBookPairPublicationRecovery.completionUncertain(record);
  }

  private static SqlitePairPublicationReconciliationRecovered recovered(
      SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    return new SqlitePairPublicationReconciliationRecovered(
        checkedRecord.binding,
        new ProtectedBookPairPublicationRetention(
            new ArtifactPublicationResult(
                checkedRecord.bookTargetPath,
                new ArtifactPublicationRetention(checkedRecord.bookStagePath)),
            new ArtifactPublicationResult(
                checkedRecord.secretTargetPath,
                new ArtifactPublicationRetention(checkedRecord.secretStagePath))));
  }
}
