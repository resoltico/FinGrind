package dev.erst.fingrind.runtime;

import dev.erst.fingrind.core.IdempotencyKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory posting store for tests and non-durable runtime composition. */
public final class InMemoryPostingFactStore implements PostingFactStore {
  private final Map<IdempotencyKey, PostingFact> postingsByIdempotencyKey =
      new ConcurrentHashMap<>();

  @Override
  public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
    return Optional.ofNullable(postingsByIdempotencyKey.get(idempotencyKey));
  }

  @Override
  public PostingFact commit(PostingFact postingFact) {
    IdempotencyKey idempotencyKey = postingFact.provenance().idempotencyKey();
    PostingFact existingPosting = postingsByIdempotencyKey.putIfAbsent(idempotencyKey, postingFact);
    if (existingPosting != null) {
      throw new IllegalStateException("Duplicate idempotency key for book.");
    }
    return postingFact;
  }
}
