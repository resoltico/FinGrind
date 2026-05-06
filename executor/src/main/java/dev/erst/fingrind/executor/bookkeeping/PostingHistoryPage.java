package dev.erst.fingrind.executor.bookkeeping;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping page of committed postings. */
public record PostingHistoryPage(
    List<CommittedPosting> postings, int limit, Optional<PostingHistoryCursor> nextCursor) {
  /** Validates one local bookkeeping page of committed postings. */
  public PostingHistoryPage {
    Objects.requireNonNull(postings, "postings");
    Objects.requireNonNull(nextCursor, "nextCursor");
    postings = List.copyOf(postings);
  }

  /** Returns whether one further posting-history page is available. */
  public boolean hasMore() {
    return nextCursor.isPresent();
  }
}
