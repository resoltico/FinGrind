package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Optional;

/** Looks up committed posting facts by their stable identities. */
public interface PostingLookupStore {
  /** Looks up one existing posting fact by book-local idempotency identity. */
  Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey);

  /** Looks up one existing posting fact by durable posting identity. */
  Optional<CommittedPosting> findPosting(PostingId postingId);

  /** Looks up an existing full reversal for one prior posting, if such a reversal exists. */
  Optional<CommittedPosting> findReversalFor(PostingId priorPostingId);
}
