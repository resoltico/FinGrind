package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Admission-time classification of pair-publication evidence found beside either final target. */
sealed interface SqlitePairPublicationEvidenceScan
    permits SqlitePairPublicationEvidenceAbsent,
        SqlitePairPublicationEvidenceExact,
        SqlitePairPublicationEvidenceExactIncomplete,
        SqlitePairPublicationEvidenceOtherPending,
        SqlitePairPublicationEvidenceUnsafe {}

/** No current pair-publication evidence constrains the requested target pair. */
enum SqlitePairPublicationEvidenceAbsent implements SqlitePairPublicationEvidenceScan {
  INSTANCE
}

/** One exact immutable record is complete and can be recovered or recognized idempotently. */
record SqlitePairPublicationEvidenceExact(SqliteProtectedBookPairPublicationRecord record)
    implements SqlitePairPublicationEvidenceScan {
  SqlitePairPublicationEvidenceExact {
    Objects.requireNonNull(record, "record");
  }
}

/** One exact immutable record lacks complete evidence and blocks alternative publication. */
record SqlitePairPublicationEvidenceExactIncomplete(SqliteProtectedBookPairPublicationRecord record)
    implements SqlitePairPublicationEvidenceScan {
  SqlitePairPublicationEvidenceExactIncomplete {
    Objects.requireNonNull(record, "record");
  }
}

/** Another pending immutable record owns at least one requested publication domain. */
record SqlitePairPublicationEvidenceOtherPending(SqliteProtectedBookPairPublicationRecord record)
    implements SqlitePairPublicationEvidenceScan {
  SqlitePairPublicationEvidenceOtherPending {
    Objects.requireNonNull(record, "record");
  }
}

/** Evidence exists but is malformed, conflicting, or otherwise unsafe to adopt. */
enum SqlitePairPublicationEvidenceUnsafe implements SqlitePairPublicationEvidenceScan {
  INSTANCE
}
