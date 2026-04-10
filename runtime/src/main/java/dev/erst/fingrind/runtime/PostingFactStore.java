package dev.erst.fingrind.runtime;

import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.util.Optional;

/** Narrow runtime persistence seam for committed posting facts. */
public interface PostingFactStore {
  /** Looks up one existing posting fact by book-local idempotency identity. */
  Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey);

  /** Looks up one existing posting fact by its durable posting identity. */
  Optional<PostingFact> findByPostingId(PostingId postingId);

  /** Looks up an existing full reversal for one prior posting, if such a reversal exists. */
  Optional<PostingFact> findReversalFor(PostingId priorPostingId);

  /** Attempts one durable commit and returns the ordinary runtime outcome explicitly. */
  PostingCommitResult commit(PostingFact postingFact);
}
