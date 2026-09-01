package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalLine;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Prevents accepted postings from creating report aggregates outside the published money range. */
final class LedgerAggregateMoneyRangePolicy {
  Optional<BookkeepingPostingRejection.EntrySemanticsViolation> rejectionFor(
      AcceptedPosting candidate, PostingValidationStore book) {
    Map<AggregateKey, Long> totals = new ConcurrentHashMap<>();
    for (CommittedPosting posting :
        book.postings(dev.erst.fingrind.core.EffectiveDateRange.unbounded())) {
      for (JournalLine line : posting.journalEntry().lines()) {
        if (!add(totals, key(line), line.amount().minorUnits())) {
          return Optional.of(violation(candidate, line));
        }
      }
    }
    for (JournalLine line : candidate.journalEntry().lines()) {
      if (!add(totals, key(line), line.amount().minorUnits())) {
        return Optional.of(violation(candidate, line));
      }
    }
    return Optional.empty();
  }

  private static boolean add(Map<AggregateKey, Long> totals, AggregateKey key, long amount) {
    try {
      totals.merge(java.util.Objects.requireNonNull(key, "key"), amount, Math::addExact);
      return true;
    } catch (ArithmeticException exception) {
      return false;
    }
  }

  private static AggregateKey key(JournalLine line) {
    return new AggregateKey(line.accountCode(), line.amount().currencyUnit(), line.side());
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation violation(
      AcceptedPosting candidate, JournalLine line) {
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.ledgerAggregateMoneyRangeExceeded(
            candidate.postingOriginKind().wireValue(),
            line.accountCode(),
            line.amount().currencyUnit().code()));
  }

  private record AggregateKey(
      AccountCode accountCode, CurrencyUnit currencyUnit, JournalLine.EntrySide entrySide) {}
}
