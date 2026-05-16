package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;

/** Reads paginated posting-history views from the selected book. */
@FunctionalInterface
public interface PostingHistoryStore {
  /** Returns one filtered page of postings in a stable order from one initialized book. */
  PostingHistoryPage listPostings(PostingHistoryQuery query);
}
