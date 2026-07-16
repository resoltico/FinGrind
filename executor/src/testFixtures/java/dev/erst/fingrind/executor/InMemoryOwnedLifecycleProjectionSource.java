package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** Supplies the committed facts from which owned lifecycle test projections are reconstructed. */
interface InMemoryOwnedLifecycleProjectionSource {
  /** Returns the fixture lock protecting the retained posting facts. */
  ReentrantLock lifecycleLock();

  /** Returns the committed postings from which active lifecycle state is reconstructed. */
  Map<PostingId, CommittedPosting> lifecyclePostingsByPostingId();

  /** Returns reversals keyed by the original posting whose active state they negate. */
  Map<PostingId, CommittedPosting> lifecycleReversalsByPriorPostingId();
}
