package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Classifies durable pair-publication evidence before admission can reserve new targets. */
final class SqliteProtectedBookPairPublicationRecovery {
  private final SqliteProtectedBookPairPublicationRecoveryPublisher publisher;

  SqliteProtectedBookPairPublicationRecovery(
      SqliteProtectedBookPairPublicationPreparation.RecoveredPairVerifier recoveredPairVerifier,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer) {
    publisher =
        new SqliteProtectedBookPairPublicationRecoveryPublisher(
            recoveredPairVerifier, directoryForcer, recoveryRecordFileForcer);
  }

  SqlitePairPublicationReconciliation reconcile(
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy expectedBookTargetPolicy,
      ProtectedBookPairPublicationRecoveryRequest request,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    Path checkedBookTarget = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    Path checkedSecretTarget = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    RestoredBookTargetPolicy checkedPolicy =
        Objects.requireNonNull(expectedBookTargetPolicy, "expectedBookTargetPolicy");
    ProtectedBookPairPublicationRecoveryRequest checkedRequest =
        Objects.requireNonNull(request, "request");
    SqlitePairPublicationEvidenceScan evidence =
        SqliteProtectedBookPairPublicationRecord.scanForAdmission(
            checkedBookTarget, checkedSecretTarget);
    return switch (evidence) {
      case SqlitePairPublicationEvidenceAbsent _ ->
          reconcileAbsent(checkedBookTarget, checkedSecretTarget, checkedPolicy, checkedRequest);
      case SqlitePairPublicationEvidenceExact exact ->
          publisher.recover(
              exact.record(),
              checkedPolicy,
              checkedRequest,
              Objects.requireNonNull(bookArtifactRole, "bookArtifactRole"),
              Objects.requireNonNull(secretArtifactRole, "secretArtifactRole"),
              false);
      case SqlitePairPublicationEvidenceExactIncomplete incomplete ->
          publisher.recover(
              incomplete.record(),
              checkedPolicy,
              checkedRequest,
              Objects.requireNonNull(bookArtifactRole, "bookArtifactRole"),
              Objects.requireNonNull(secretArtifactRole, "secretArtifactRole"),
              true);
      case SqlitePairPublicationEvidenceOtherPending pending ->
          throw recoveryPending(pending.record());
      case SqlitePairPublicationEvidenceUnsafe _ ->
          evidenceBlocked(checkedBookTarget, checkedSecretTarget);
    };
  }

  private static SqlitePairPublicationReconciliation reconcileAbsent(
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy expectedBookTargetPolicy,
      ProtectedBookPairPublicationRecoveryRequest request) {
    PairTargetPresence targets = PairTargetPresence.observe(bookTargetPath, secretTargetPath);
    if (isRekeyWithOnlyExpectedLiveBook(expectedBookTargetPolicy, targets)) {
      return SqlitePairPublicationReconciliationAbsent.INSTANCE;
    }
    rejectRekeySecretWithoutStage(
        expectedBookTargetPolicy, targets, bookTargetPath, secretTargetPath);
    rejectUnexpectedExistingBook(
        expectedBookTargetPolicy, request, targets, bookTargetPath, secretTargetPath);
    if (targets.areBothAbsent()) {
      return SqlitePairPublicationReconciliationAbsent.INSTANCE;
    }
    rejectUnboundSecretWithoutBook(targets, bookTargetPath, secretTargetPath);
    return existingCompleteBackupOrEvidenceBlocked(request, bookTargetPath, secretTargetPath);
  }

  private static boolean isRekeyWithOnlyExpectedLiveBook(
      RestoredBookTargetPolicy expectedBookTargetPolicy, PairTargetPresence targets) {
    return expectedBookTargetPolicy == RestoredBookTargetPolicy.REPLACE_SELECTED
        && targets.bookExists()
        && !targets.secretExists();
  }

  private static void rejectRekeySecretWithoutStage(
      RestoredBookTargetPolicy expectedBookTargetPolicy,
      PairTargetPresence targets,
      Path bookTargetPath,
      Path secretTargetPath) {
    if (expectedBookTargetPolicy != RestoredBookTargetPolicy.REPLACE_SELECTED
        || !targets.bookExists()
        || !targets.secretExists()
        || hasUnboundStageResidue(bookTargetPath, secretTargetPath)) {
      return;
    }
    throw secretTargetOccupied(secretTargetPath);
  }

  private static void rejectUnexpectedExistingBook(
      RestoredBookTargetPolicy expectedBookTargetPolicy,
      ProtectedBookPairPublicationRecoveryRequest request,
      PairTargetPresence targets,
      Path bookTargetPath,
      Path secretTargetPath) {
    if (expectedBookTargetPolicy != RestoredBookTargetPolicy.REQUIRE_ABSENT
        || !targets.bookExists()
        || hasUnboundStageResidue(bookTargetPath, secretTargetPath)) {
      return;
    }
    throw new ProtectedBookMaintenanceRejectionException(
        switch (request) {
          case ProtectedBookPairPublicationRecoveryRequest.Backup _ ->
              new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(bookTargetPath);
          case ProtectedBookPairPublicationRecoveryRequest.Restore _ ->
              new ProtectedBookMaintenanceRejection.BookDestinationOccupied(bookTargetPath);
          case ProtectedBookPairPublicationRecoveryRequest.Rekey _ ->
              throw new IllegalStateException(
                  "A rekey pair publication must replace its selected live book target.");
        });
  }

  private static void rejectUnboundSecretWithoutBook(
      PairTargetPresence targets, Path bookTargetPath, Path secretTargetPath) {
    if (targets.bookExists()
        || !targets.secretExists()
        || hasUnboundStageResidue(bookTargetPath, secretTargetPath)) {
      return;
    }
    throw secretTargetOccupied(secretTargetPath);
  }

  private static ProtectedBookMaintenanceRejectionException secretTargetOccupied(
      Path secretTargetPath) {
    return new ProtectedBookMaintenanceRejectionException(
        new ProtectedBookMaintenanceRejection.SecretTargetOccupied(secretTargetPath));
  }

  static SqlitePairPublicationReconciliation existingCompleteBackupOrEvidenceBlocked(
      ProtectedBookPairPublicationRecoveryRequest request,
      Path bookTargetPath,
      Path secretTargetPath) {
    if (request instanceof ProtectedBookPairPublicationRecoveryRequest.Backup
        && Files.isRegularFile(bookTargetPath, LinkOption.NOFOLLOW_LINKS)
        && Files.isRegularFile(secretTargetPath, LinkOption.NOFOLLOW_LINKS)) {
      return new SqlitePairPublicationReconciliationExistingCompleteBackup(
          bookTargetPath, secretTargetPath);
    }
    return evidenceBlocked(bookTargetPath, secretTargetPath);
  }

  /** Holds the non-following existence facts observed before absent-record reconciliation. */
  private record PairTargetPresence(boolean bookExists, boolean secretExists) {
    private static PairTargetPresence observe(Path bookTargetPath, Path secretTargetPath) {
      return new PairTargetPresence(
          Files.exists(bookTargetPath, LinkOption.NOFOLLOW_LINKS),
          Files.exists(secretTargetPath, LinkOption.NOFOLLOW_LINKS));
    }

    private boolean areBothAbsent() {
      return !bookExists && !secretExists;
    }
  }

  /**
   * Distinguishes an ordinary occupied generated-secret path from an unbound owned-stage residue.
   *
   * <p>A caller-selected key file with no companion ownership evidence is a precise no-overwrite
   * refusal. Once either final member has an owned stage record, its relation to the visible final
   * member cannot be established from an immutable pair record, so admission must remain
   * fail-closed instead of misreporting it as an ordinary occupied key.
   */
  private static boolean hasUnboundStageResidue(Path bookTargetPath, Path secretTargetPath) {
    return !SqliteOwnedStageRecord.findFor(bookTargetPath).isEmpty()
        || !SqliteOwnedStageRecord.findFor(secretTargetPath).isEmpty();
  }

  static ProtectedBookMaintenanceRejectionException recoveryPending(
      SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    return new ProtectedBookMaintenanceRejectionException(
        new ProtectedBookMaintenanceRejection.RecoveryPending(
            checkedRecord.recoveryOperation(),
            checkedRecord.bookTargetPath,
            checkedRecord.secretTargetPath));
  }

  static SqlitePairPublicationReconciliation completionUncertain(
      SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    ProtectedBookPairPublicationMemberState bookState =
        SqliteProtectedBookPairPublicationRecoverySupport.bookState(checkedRecord);
    ProtectedBookPairPublicationMemberState secretState =
        SqliteProtectedBookPairPublicationRecoverySupport.secretState(checkedRecord);
    return completionUncertain(
        checkedRecord.bookTargetPath,
        bookState,
        checkedRecord.secretTargetPath,
        secretState,
        SqliteProtectedBookPairPublicationRecoverySupport.hasOwnedStages(checkedRecord)
            ? retainedPublicationStages(checkedRecord)
            : null);
  }

  static SqlitePairPublicationReconciliationCompletionUncertain completionUncertain(
      SqliteProtectedBookPairPublicationRecord record,
      ProtectedBookPairPublicationMemberState bookState,
      ProtectedBookPairPublicationMemberState secretState) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    return completionUncertain(
        checkedRecord.bookTargetPath,
        Objects.requireNonNull(bookState, "bookState"),
        checkedRecord.secretTargetPath,
        Objects.requireNonNull(secretState, "secretState"),
        retainedPublicationStages(checkedRecord));
  }

  static SqlitePairPublicationReconciliationEvidenceBlocked evidenceBlocked(
      SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    return evidenceBlocked(checkedRecord.bookTargetPath, checkedRecord.secretTargetPath);
  }

  static SqlitePairPublicationReconciliationEvidenceBlocked evidenceBlocked(
      Path bookPath, Path secretPath) {
    return new SqlitePairPublicationReconciliationEvidenceBlocked(
        bookPath,
        ProtectedBookPairPublicationMemberState.UNESTABLISHED,
        secretPath,
        ProtectedBookPairPublicationMemberState.UNESTABLISHED,
        null);
  }

  static SqlitePairPublicationReconciliationCompletionUncertain completionUncertain(
      Path bookPath,
      ProtectedBookPairPublicationMemberState bookState,
      Path secretPath,
      ProtectedBookPairPublicationMemberState secretState,
      @org.jspecify.annotations.Nullable ProtectedBookPairPublicationRetention pairPublicationRetention) {
    return new SqlitePairPublicationReconciliationCompletionUncertain(
        bookPath, bookState, secretPath, secretState, pairPublicationRetention);
  }

  static SqlitePairPublicationReconciliationPrepublicationRecoveryRequired
      prepublicationRecoveryRequired(
          SqliteProtectedBookPairPublicationRecord record,
          ProtectedBookPairPublicationRecoveryRecordState recoveryRecordState) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    return new SqlitePairPublicationReconciliationPrepublicationRecoveryRequired(
        checkedRecord.bookTargetPath,
        checkedRecord.secretTargetPath,
        Objects.requireNonNull(recoveryRecordState, "recoveryRecordState"),
        retainedPublicationStages(checkedRecord));
  }

  private static ProtectedBookPairPublicationRetention retainedPublicationStages(
      SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    return new ProtectedBookPairPublicationRetention(
        new ArtifactPublicationResult(
            checkedRecord.bookTargetPath,
            new ArtifactPublicationRetention(checkedRecord.bookStagePath)),
        new ArtifactPublicationResult(
            checkedRecord.secretTargetPath,
            new ArtifactPublicationRetention(checkedRecord.secretStagePath)));
  }
}
