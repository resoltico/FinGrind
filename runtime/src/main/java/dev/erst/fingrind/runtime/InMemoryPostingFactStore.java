package dev.erst.fingrind.runtime;

import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReference;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory posting store for tests and non-durable runtime composition. */
public final class InMemoryPostingFactStore implements PostingFactStore {
  private final Map<IdempotencyKey, PostingFact> postingsByIdempotencyKey =
      new ConcurrentHashMap<>();
  private final Map<PostingId, PostingFact> postingsByPostingId = new ConcurrentHashMap<>();
  private final Map<PostingId, PostingFact> reversalsByPriorPostingId = new ConcurrentHashMap<>();

  @Override
  public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
    return Optional.ofNullable(postingsByIdempotencyKey.get(idempotencyKey));
  }

  @Override
  public Optional<PostingFact> findByPostingId(PostingId postingId) {
    return Optional.ofNullable(postingsByPostingId.get(postingId));
  }

  @Override
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    return Optional.ofNullable(reversalsByPriorPostingId.get(priorPostingId));
  }

  @Override
  public PostingCommitResult commit(PostingFact postingFact) {
    IdempotencyKey idempotencyKey = postingFact.provenance().requestProvenance().idempotencyKey();
    PostingFact existingPosting = postingsByIdempotencyKey.putIfAbsent(idempotencyKey, postingFact);
    if (existingPosting != null) {
      return new PostingCommitResult.DuplicateIdempotency(idempotencyKey);
    }
    postingsByPostingId.put(postingFact.postingId(), postingFact);

    Optional<ReversalReference> reversalReference = postingFact.reversalReference();
    if (reversalReference.isPresent()) {
      ReversalReference postedReversal = reversalReference.orElseThrow();
      PostingId priorPostingId = postedReversal.priorPostingId();
      PostingFact existingReversal =
          reversalsByPriorPostingId.putIfAbsent(priorPostingId, postingFact);
      if (existingReversal != null) {
        postingsByIdempotencyKey.remove(idempotencyKey, postingFact);
        postingsByPostingId.remove(postingFact.postingId(), postingFact);
        return new PostingCommitResult.DuplicateReversalTarget(priorPostingId);
      }
    }
    return new PostingCommitResult.Committed(postingFact);
  }
}
