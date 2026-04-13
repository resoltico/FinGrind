package dev.erst.fingrind.application;

import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.util.Optional;

/** Application-owned book session seam for committed posting facts. */
public interface BookSession extends AutoCloseable {
  /** Looks up one existing posting fact by book-local idempotency identity. */
  Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey);

  /** Looks up one existing posting fact by its durable posting identity. */
  Optional<PostingFact> findByPostingId(PostingId postingId);

  /** Looks up an existing full reversal for one prior posting, if such a reversal exists. */
  Optional<PostingFact> findReversalFor(PostingId priorPostingId);

  /** Attempts one durable commit and returns the ordinary application outcome explicitly. */
  PostingCommitResult commit(PostingFact postingFact);

  @Override
  void close();
}
