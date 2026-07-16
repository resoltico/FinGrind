package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.core.PostingId;

/** Posting inspection and history read capability exposed by {@link BookReadService}. */
public sealed interface BookReadPostingOperations permits BookReadService {
  /** Returns one committed posting by durable posting identity. */
  default GetPostingResult getPosting(PostingId postingId) {
    return ((BookReadService) this).postingQueries().getPosting(postingId);
  }

  /** Returns one filtered page of committed postings. */
  default ListPostingsResult listPostings(ListPostingsQuery query) {
    return ((BookReadService) this).postingQueries().listPostings(query);
  }
}
