package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import org.jspecify.annotations.Nullable;

/** Rebuilds one persisted posting origin into its caller-authored entry. */
@FunctionalInterface
interface SqlitePostingOriginatingEntryBuilder {
  /** Rebuilds one caller-authored entry from the persisted posting origin and resolved facts. */
  BookkeepingEntry build(
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails);
}
