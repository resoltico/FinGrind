package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.spi.AttestationPostingCommitmentStore;
import dev.erst.fingrind.executor.spi.PostingHistoryStore;
import dev.erst.fingrind.executor.spi.PostingLookupStore;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared posting-lookup and posting-history defaults for SQLite read wrappers. */
interface SqliteReadPostingLookupCapabilityView
    extends AttestationPostingCommitmentStore,
        PostingLookupStore,
        PostingHistoryStore,
        SqliteLifecycleInspectionCapabilityView {
  @Override
  default Map<PostingId, AttestationCommit> attestationCommitsFor(Set<PostingId> postingIds) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingLookup().attestationCommitsFor(postingIds);
  }

  @Override
  default Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingLookup().findExistingPosting(idempotencyKey);
  }

  @Override
  default Optional<CommittedPosting> findPosting(PostingId postingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingLookup().findPosting(postingId);
  }

  @Override
  default Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingLookup().findReversalFor(priorPostingId);
  }

  @Override
  default PostingHistoryPage listPostings(PostingHistoryQuery query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingLookup().listPostings(query);
  }
}
