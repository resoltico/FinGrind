package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.List;

/** Read/query surface over one SQLite posting-fact store. */
interface SqlitePostingFactStoreReadView
    extends SqlitePostingFactStorePostingHistoryView,
        SqlitePostingFactStoreAccountCatalogView,
        SqlitePostingFactStorePostingLookupView,
        SqlitePostingFactStoreReportingView {
  @Override
  default List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().reporting().postings(effectiveDateRange);
  }
}
