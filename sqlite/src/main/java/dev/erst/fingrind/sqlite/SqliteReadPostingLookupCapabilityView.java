package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.spi.PostingHistoryStore;
import dev.erst.fingrind.executor.spi.PostingLookupStore;
import java.util.Optional;

/** Shared posting-lookup and posting-history defaults for SQLite read wrappers. */
interface SqliteReadPostingLookupCapabilityView
    extends PostingLookupStore, PostingHistoryStore, SqliteLifecycleInspectionCapabilityView {
  @Override
  default Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().findExistingPosting(idempotencyKey);
  }

  @Override
  default Optional<CommittedPosting> findPosting(PostingId postingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().findPosting(postingId);
  }

  @Override
  default Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().findReversalFor(priorPostingId);
  }

  @Override
  default PostingHistoryPage listPostings(PostingHistoryQuery query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().listPostings(query);
  }
}
