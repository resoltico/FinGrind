package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Reconciles one already-authorized final member of a protected-book pair. */
final class SqlitePairPublicationMemberReconciler {
  private final SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer;
  private final SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer
      recoveryRecordFileForcer;

  SqlitePairPublicationMemberReconciler(
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer) {
    this.directoryForcer = Objects.requireNonNull(directoryForcer, "directoryForcer");
    this.recoveryRecordFileForcer =
        Objects.requireNonNull(recoveryRecordFileForcer, "recoveryRecordFileForcer");
  }

  SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation reconcileSecret(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan plan,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
    if (plan
        == SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan
            .MATCHED_OR_FORCEABLE) {
      return forceSecret(record.secretTargetPath);
    }
    if (!canPublishSecret(record, plan)) {
      return uncertain();
    }
    @org.jspecify.annotations.Nullable SqliteOwnedStageRecord owner =
        SqliteProtectedBookPairPublicationRecoverySupport.ownedStage(
            record.secretTargetPath, record.secretStagePath);
    if (owner == null || !publishSecret(record, owner, capabilityWitnesses)) {
      return uncertain();
    }
    return forceSecret(record.secretTargetPath);
  }

  SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation reconcileBook(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan plan,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
    if (plan
        == SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan
            .MATCHED_OR_FORCEABLE) {
      return forceBook(record.bookTargetPath);
    }
    if (!canPublishBook(record, plan)) {
      return uncertain();
    }
    @org.jspecify.annotations.Nullable SqliteOwnedStageRecord owner =
        SqliteProtectedBookPairPublicationRecoverySupport.ownedStage(
            record.bookTargetPath, record.bookStagePath);
    if (owner == null || !publishBook(record, owner, capabilityWitnesses)) {
      return uncertain();
    }
    return record.finalBookMatches() ? forceBook(record.bookTargetPath) : uncertain();
  }

  private boolean canPublishSecret(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan plan) {
    return plan
            == SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE
        && !Files.exists(record.secretTargetPath, LinkOption.NOFOLLOW_LINKS)
        && record.stagedSecretMatches();
  }

  private boolean publishSecret(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteOwnedStageRecord owner,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
    try {
      forceRecoveryEvidence(record);
      owner.requireIntactFor(record.secretTargetPath);
      if (!record.stagedSecretMatches() || !rekeyTargetCanPublishSecret(record)) {
        return false;
      }
      capabilityWitnesses.requireCurrent(
          record.secretTargetPath,
          SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
      Files.createLink(record.secretTargetPath, record.secretStagePath);
      return true;
    } catch (java.nio.file.FileAlreadyExistsException collision) {
      return record.finalSecretMatches();
    } catch (IOException | RuntimeException failure) {
      return false;
    }
  }

  private boolean canPublishBook(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan plan) {
    return plan
            == SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE
        && record.stagedBookMatches();
  }

  private boolean publishBook(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteOwnedStageRecord owner,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
    return switch (record.bookTargetPolicy) {
      case REQUIRE_ABSENT -> publishAbsentBook(record, owner, capabilityWitnesses);
      case REPLACE_SELECTED -> publishReplacingBook(record, owner, capabilityWitnesses);
    };
  }

  private boolean publishAbsentBook(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteOwnedStageRecord owner,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
    if (Files.exists(record.bookTargetPath, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    try {
      forceRecoveryEvidence(record);
      owner.requireIntactFor(record.bookTargetPath);
      if (!record.stagedBookMatches()) {
        return false;
      }
      capabilityWitnesses.requireCurrent(
          record.bookTargetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
      Files.createLink(record.bookTargetPath, record.bookStagePath);
      return true;
    } catch (java.nio.file.FileAlreadyExistsException collision) {
      return record.finalBookMatches();
    } catch (IOException | RuntimeException failure) {
      return false;
    }
  }

  private boolean publishReplacingBook(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteOwnedStageRecord owner,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
    try {
      forceRecoveryEvidence(record);
      owner.requireIntactFor(record.bookTargetPath);
      if (!record.stagedBookMatches() || !record.replaceTargetMatches()) {
        return false;
      }
      capabilityWitnesses.requireCurrent(
          record.bookTargetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
      Path replacementBridge =
          SqliteProtectedBookPublicationSupport.createReplacementBridgeRetainingStage(
              record.bookStagePath, record.bookTargetPath, Files::createLink);
      capabilityWitnesses.requireCurrent(
          record.bookTargetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE);
      SqliteProtectedBookPublicationSupport.moveReplacing(replacementBridge, record.bookTargetPath);
      return true;
    } catch (java.nio.file.FileAlreadyExistsException collision) {
      return record.finalBookMatches();
    } catch (IOException | RuntimeException failure) {
      return false;
    }
  }

  private void forceRecoveryEvidence(SqliteProtectedBookPairPublicationRecord record)
      throws IOException {
    SqliteProtectedBookPairPublicationEvidenceLifecycle.forceForRecoveredPublication(
        record, directoryForcer, recoveryRecordFileForcer);
  }

  private static boolean rekeyTargetCanPublishSecret(
      SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    return checkedRecord.bookTargetPolicy != RestoredBookTargetPolicy.REPLACE_SELECTED
        || checkedRecord.finalBookMatches()
        || checkedRecord.replaceTargetMatches();
  }

  private SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation forceSecret(
      Path targetPath) {
    return SqliteProtectedBookPairPublicationRecoverySupport.forceRecoveredMember(
        directoryForcer,
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
            .GENERATED_SECRET_PUBLICATION,
        targetPath);
  }

  private SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation forceBook(
      Path targetPath) {
    return SqliteProtectedBookPairPublicationRecoverySupport.forceRecoveredMember(
        directoryForcer,
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.BOOK_PUBLICATION,
        targetPath);
  }

  private static SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation
      uncertain() {
    return SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN;
  }
}
