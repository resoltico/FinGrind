package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;

/** Routes committed lifecycle facts to the durable owner for each owned business context. */
final class SqliteOwnedContextWriter {
  private SqliteOwnedContextWriter() {}

  static void persist(SqliteNativeDatabase database, CommittedPosting posting) {
    BookkeepingEntry resolvedEntry =
        posting.resolvedOriginatingEntry().or(() -> posting.callerAuthoredEntry()).orElse(null);
    if (resolvedEntry == null) {
      return;
    }
    SqliteFixedAssetContextWriter.persist(database, posting, resolvedEntry);
    SqliteFinancingContextWriter.persist(database, posting, resolvedEntry);
    SqliteRealizedForeignExchangeContextWriter.persist(database, posting, resolvedEntry);
    if (posting.callerAuthoredEntry().orElse(null) instanceof BookkeepingEntry.Reversal) {
      String reversalPostingId = posting.postingId().value();
      String priorPostingId = posting.reversalReference().orElseThrow().priorPostingId().value();
      SqliteOwnedContextReversalWriter.persist(database, reversalPostingId, priorPostingId);
    }
  }
}
