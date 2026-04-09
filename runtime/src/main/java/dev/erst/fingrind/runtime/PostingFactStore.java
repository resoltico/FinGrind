package dev.erst.fingrind.runtime;

import dev.erst.fingrind.core.IdempotencyKey;
import java.util.Optional;

/** Narrow runtime persistence seam for committed posting facts. */
public interface PostingFactStore {
  /** Looks up one existing posting fact by book-local idempotency identity. */
  Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey);

  /** Commits one posting fact or throws when the persistence contract rejects it. */
  PostingFact commit(PostingFact postingFact);
}
