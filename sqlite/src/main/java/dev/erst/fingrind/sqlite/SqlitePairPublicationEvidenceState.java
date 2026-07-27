package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Queries immutable evidence state without deciding admission policy. */
final class SqlitePairPublicationEvidenceState {
  private SqlitePairPublicationEvidenceState() {}

  static boolean retainedEvidenceIsIncomplete(SqliteProtectedBookPairPublicationRecord record) {
    return evidenceIsIncomplete(record, SqliteProtectedBookPairPublicationEvidenceKind.RETAINED);
  }

  static boolean completionEvidenceIsIncomplete(SqliteProtectedBookPairPublicationRecord record) {
    return evidenceIsIncomplete(record, SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED);
  }

  static boolean isDurablyCompleted(SqliteProtectedBookPairPublicationRecord record) {
    return SqliteProtectedBookPairPublicationEvidenceLifecycle.hasComplete(
        Objects.requireNonNull(record, "record"),
        SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED);
  }

  static boolean isCompleteClaimOnly(SqliteProtectedBookPairPublicationRecord record) {
    return SqliteProtectedBookPairPublicationEvidenceLifecycle.hasComplete(
            Objects.requireNonNull(record, "record"),
            SqliteProtectedBookPairPublicationEvidenceKind.CLAIM)
        && !SqliteProtectedBookPairPublicationEvidenceLifecycle.hasObserved(
            record, SqliteProtectedBookPairPublicationEvidenceKind.INTENT)
        && !SqliteProtectedBookPairPublicationEvidenceLifecycle.hasObserved(
            record, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY)
        && !SqliteProtectedBookPairPublicationEvidenceLifecycle.hasObserved(
            record, SqliteProtectedBookPairPublicationEvidenceKind.RETAINED);
  }

  static boolean hasNoAuthorizationEvidence(SqliteProtectedBookPairPublicationRecord record) {
    return !SqliteProtectedBookPairPublicationEvidenceLifecycle.hasObserved(
            Objects.requireNonNull(record, "record"),
            SqliteProtectedBookPairPublicationEvidenceKind.INTENT)
        && !SqliteProtectedBookPairPublicationEvidenceLifecycle.hasObserved(
            record, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY);
  }

  static boolean isCompleteRetainedPrepublication(SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    return SqliteProtectedBookPairPublicationEvidenceLifecycle.hasComplete(
            checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM)
        && SqliteProtectedBookPairPublicationEvidenceLifecycle.hasComplete(
            checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.INTENT)
        && SqliteProtectedBookPairPublicationEvidenceLifecycle.hasComplete(
            checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY)
        && SqliteProtectedBookPairPublicationEvidenceLifecycle.hasComplete(
            checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.RETAINED);
  }

  private static boolean evidenceIsIncomplete(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind evidenceKind) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    SqliteProtectedBookPairPublicationEvidenceKind checkedKind =
        Objects.requireNonNull(evidenceKind, "evidenceKind");
    return SqliteProtectedBookPairPublicationEvidenceLifecycle.hasObserved(
            checkedRecord, checkedKind)
        && !SqliteProtectedBookPairPublicationEvidenceLifecycle.hasComplete(
            checkedRecord, checkedKind);
  }
}
