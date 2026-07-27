package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

/** Derives one deterministic admission result from decoded pair-publication records. */
final class SqlitePairPublicationEvidenceClassifier {
  private SqlitePairPublicationEvidenceClassifier() {}

  static SqlitePairPublicationEvidenceScan classify(
      Path bookTargetPath,
      Path secretTargetPath,
      Map<java.util.UUID, SqliteProtectedBookPairPublicationRecord> records) {
    @org.jspecify.annotations.Nullable SqliteProtectedBookPairPublicationRecord exact = null;
    for (SqliteProtectedBookPairPublicationRecord record :
        records.values().stream()
            .sorted(Comparator.comparing(value -> value.pairId.toString()))
            .toList()) {
      switch (classifyRecord(record, bookTargetPath, secretTargetPath)) {
        case SqlitePairPublicationRecordIgnore _ -> {}
        case SqlitePairPublicationRecordExactCandidate(
                SqliteProtectedBookPairPublicationRecord candidate) -> {
          if (exact != null) {
            return SqlitePairPublicationEvidenceUnsafe.INSTANCE;
          }
          exact = candidate;
        }
        case SqlitePairPublicationRecordTerminal(SqlitePairPublicationEvidenceScan scan) -> {
          return scan;
        }
      }
    }
    if (exact == null) {
      return SqlitePairPublicationEvidenceAbsent.INSTANCE;
    }
    return new SqlitePairPublicationEvidenceExact(exact);
  }

  private static SqlitePairPublicationRecordDisposition classifyRecord(
      SqliteProtectedBookPairPublicationRecord record, Path bookTargetPath, Path secretTargetPath) {
    boolean targetsMatch = targetsMatch(record, bookTargetPath, secretTargetPath);
    if (SqlitePairPublicationEvidenceState.isDurablyCompleted(record)) {
      if (isCompletedBackupForSelectedArtifact(record, bookTargetPath)) {
        return new SqlitePairPublicationRecordExactCandidate(record);
      }
      return SqlitePairPublicationRecordIgnore.INSTANCE;
    }
    if (SqlitePairPublicationEvidenceState.retainedEvidenceIsIncomplete(record)) {
      return pendingDisposition(record, targetsMatch);
    }
    if (SqlitePairPublicationEvidenceState.hasNoAuthorizationEvidence(record)) {
      return noAuthorizationDisposition(record);
    }
    if (SqlitePairPublicationEvidenceState.isCompleteRetainedPrepublication(record)
        && !record.finalBookMatches()
        && !record.finalSecretMatches()) {
      return SqlitePairPublicationRecordIgnore.INSTANCE;
    }
    if (record.finalBookMatches() && record.finalSecretMatches()) {
      return targetsMatch
          ? new SqlitePairPublicationRecordExactCandidate(record)
          : SqlitePairPublicationRecordIgnore.INSTANCE;
    }
    return incompleteDisposition(record, targetsMatch);
  }

  private static SqlitePairPublicationRecordDisposition noAuthorizationDisposition(
      SqliteProtectedBookPairPublicationRecord record) {
    if (SqlitePairPublicationEvidenceState.isCompleteClaimOnly(record)
        && !record.finalBookMatches()
        && !record.finalSecretMatches()) {
      return SqlitePairPublicationRecordIgnore.INSTANCE;
    }
    return new SqlitePairPublicationRecordTerminal(SqlitePairPublicationEvidenceUnsafe.INSTANCE);
  }

  private static SqlitePairPublicationRecordDisposition incompleteDisposition(
      SqliteProtectedBookPairPublicationRecord record, boolean targetsMatch) {
    SqliteProtectedBookPairPublicationEvidenceKind kind = incompleteKind(record);
    if (kind == null) {
      return targetsMatch
          ? new SqlitePairPublicationRecordExactCandidate(record)
          : pendingOtherDisposition(record);
    }
    if (kind == SqliteProtectedBookPairPublicationEvidenceKind.CLAIM
        || (kind == SqliteProtectedBookPairPublicationEvidenceKind.INTENT
            && SqliteProtectedBookPairPublicationEvidenceLifecycle.hasObserved(
                record, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY))) {
      return new SqlitePairPublicationRecordTerminal(SqlitePairPublicationEvidenceUnsafe.INSTANCE);
    }
    return pendingDisposition(record, targetsMatch);
  }

  private static boolean targetsMatch(
      SqliteProtectedBookPairPublicationRecord record, Path bookTargetPath, Path secretTargetPath) {
    return SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
            record.bookTargetPath, bookTargetPath)
        && SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
            record.secretTargetPath, secretTargetPath);
  }

  /**
   * Keeps a completed backup record authoritative for its immutable external artifact while leaving
   * it inert for every unrelated destination that merely shares an evidence directory.
   */
  private static boolean isCompletedBackupForSelectedArtifact(
      SqliteProtectedBookPairPublicationRecord record, Path bookTargetPath) {
    return record.binding
            instanceof dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding.Backup
        && SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
            record.bookTargetPath, bookTargetPath);
  }

  private static SqlitePairPublicationRecordDisposition pendingDisposition(
      SqliteProtectedBookPairPublicationRecord record, boolean targetsMatch) {
    return new SqlitePairPublicationRecordTerminal(
        targetsMatch
            ? new SqlitePairPublicationEvidenceExactIncomplete(record)
            : new SqlitePairPublicationEvidenceOtherPending(record));
  }

  private static SqlitePairPublicationRecordDisposition pendingOtherDisposition(
      SqliteProtectedBookPairPublicationRecord record) {
    return new SqlitePairPublicationRecordTerminal(
        new SqlitePairPublicationEvidenceOtherPending(record));
  }

  private static @org.jspecify.annotations.Nullable SqliteProtectedBookPairPublicationEvidenceKind
      incompleteKind(SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        java.util.Objects.requireNonNull(record, "record");
    for (SqliteProtectedBookPairPublicationEvidenceKind kind :
        SqliteProtectedBookPairPublicationEvidenceKind.values()) {
      if (kind.isMandatoryRecoveryEvidence()
          && !SqliteProtectedBookPairPublicationEvidenceLifecycle.hasComplete(
              checkedRecord, kind)) {
        return kind;
      }
    }
    if (SqlitePairPublicationEvidenceState.completionEvidenceIsIncomplete(checkedRecord)) {
      return SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED;
    }
    return null;
  }
}
