package dev.erst.fingrind.sqlite;

/** One record's contribution to a requested protected-book pair evidence scan. */
sealed interface SqlitePairPublicationRecordDisposition
    permits SqlitePairPublicationRecordIgnore,
        SqlitePairPublicationRecordExactCandidate,
        SqlitePairPublicationRecordTerminal {}

/** The record is immutable terminal history unrelated to current admission. */
enum SqlitePairPublicationRecordIgnore implements SqlitePairPublicationRecordDisposition {
  INSTANCE
}

/** The record describes a complete exact pair or claim-only final state. */
record SqlitePairPublicationRecordExactCandidate(SqliteProtectedBookPairPublicationRecord record)
    implements SqlitePairPublicationRecordDisposition {
  SqlitePairPublicationRecordExactCandidate {
    java.util.Objects.requireNonNull(record, "record");
  }
}

/** The record blocks admission with the supplied deterministic scan result. */
record SqlitePairPublicationRecordTerminal(SqlitePairPublicationEvidenceScan scan)
    implements SqlitePairPublicationRecordDisposition {
  SqlitePairPublicationRecordTerminal {
    java.util.Objects.requireNonNull(scan, "scan");
  }
}
