package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.util.Optional;

/** Posting-lookup surface over one SQLite posting-fact store. */
interface SqlitePostingFactStorePostingLookupView extends SqlitePostingFactStoreReadOperationsView {
  /** Finds an existing committed posting by idempotency key. */
  default Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingLookup().findExistingPosting(idempotencyKey);
  }

  /** Finds one committed posting by posting id. */
  default Optional<CommittedPosting> findPosting(PostingId postingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingLookup().findPosting(postingId);
  }

  /** Finds the reversal committed for the supplied prior posting when present. */
  default Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingLookup().findReversalFor(priorPostingId);
  }

  /** Returns one page of posting history for the supplied query. */
  default PostingHistoryPage listPostings(PostingHistoryQuery query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingLookup().listPostings(query);
  }
}
