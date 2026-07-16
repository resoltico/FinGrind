package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.PostingRangeStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Posting-range and close-horizon defaults for SQLite read wrappers. */
interface SqliteReadPostingRangeCapabilityView
    extends PostingRangeStore, SqliteReadReportingCapabilityView {
  @Override
  default List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    return SqliteReadReportingCapabilityView.super.postings(effectiveDateRange);
  }

  @Override
  default Optional<LocalDate> earliestPostingEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().earliestPostingEffectiveDate();
  }

  @Override
  default Optional<LocalDate> transferredThroughEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().transferredThroughEffectiveDate();
  }
}
