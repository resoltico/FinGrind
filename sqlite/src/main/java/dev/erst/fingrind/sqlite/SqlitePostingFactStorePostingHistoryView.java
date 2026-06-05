package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Posting-history date range surface over one SQLite posting-fact store. */
interface SqlitePostingFactStorePostingHistoryView {
  /** Returns the thread-ownership guard for this store. */
  SqliteThreadOwner storeThreadOwner();

  /** Returns the read operations owner for this store. */
  SqliteStoreReadOperations storeReadOperations();

  /** Returns committed postings inside the requested effective-date range. */
  default List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().postings(effectiveDateRange);
  }

  /** Returns the earliest posting effective date when any postings exist. */
  default Optional<LocalDate> earliestPostingEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().earliestPostingEffectiveDate();
  }

  /** Returns the last effective date transferred through period-result close when available. */
  default Optional<LocalDate> transferredThroughEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().transferredThroughEffectiveDate();
  }
}
