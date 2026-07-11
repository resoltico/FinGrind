package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.core.JournalEntry;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Reversal-resolution validation shared by caller-authored bookkeeping entries. */
final class BookkeepingEntryReversalValidationSupport {
  private BookkeepingEntryReversalValidationSupport() {}

  static void requireResolvedReversal(
      LocalDate effectiveDate,
      PostingLineage.Reversal reversal,
      @Nullable JournalEntry resolvedJournalEntry,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(reversal, "reversal");
    if (resolvedJournalEntry != null) {
      if (!resolvedJournalEntry.effectiveDate().equals(effectiveDate)) {
        throw new IllegalArgumentException(
            "resolvedJournalEntry effectiveDate must match reversal effectiveDate.");
      }
      BookkeepingEntryForeignExchangeValidationSupport.requireDirectJournalForeignExchange(
          resolvedJournalEntry, foreignExchangeDetails);
    }
  }

  static JournalEntry requireResolvedJournalEntry(
      @Nullable JournalEntry resolvedJournalEntry, String entryKind) {
    if (resolvedJournalEntry == null) {
      throw new IllegalStateException(
          entryKind
              + " journalEntry is derived from the referenced posting and becomes available only after executor resolution.");
    }
    return resolvedJournalEntry;
  }
}
