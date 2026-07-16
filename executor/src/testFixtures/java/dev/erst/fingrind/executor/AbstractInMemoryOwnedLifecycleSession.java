package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** Shared in-memory source for owned lifecycle projections reconstructed from posting facts. */
abstract class AbstractInMemoryOwnedLifecycleSession extends AbstractInMemoryLatvianPayrollSession
    implements InMemoryFixedAssetLookupProjection,
        InMemoryFinancingLookupProjection,
        InMemoryRealizedForeignExchangeLookupProjection {
  @Override
  public final ReentrantLock lifecycleLock() {
    return lock;
  }

  @Override
  public final Map<PostingId, CommittedPosting> lifecyclePostingsByPostingId() {
    return postingsByPostingId();
  }

  @Override
  public final Map<PostingId, CommittedPosting> lifecycleReversalsByPriorPostingId() {
    return reversalsByPriorPostingId();
  }
}
