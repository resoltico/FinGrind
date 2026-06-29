package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import java.util.List;

/** Reporting read surface over one SQLite posting-fact store. */
interface SqlitePostingFactStoreReportingView extends SqliteReportingReadOperationsView {
  /** Returns committed postings inside one reporting window. */
  @Override
  default List<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> postings(
      EffectiveDateRange effectiveDateRange) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().reporting().postings(effectiveDateRange);
  }
}
