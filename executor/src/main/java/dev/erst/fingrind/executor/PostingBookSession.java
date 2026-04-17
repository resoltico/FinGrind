package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.PostingFact;
import java.util.Objects;

/** Posting-only seam for validating and committing journal entries. */
public interface PostingBookSession extends PostingValidationBook, AutoCloseable {
  /** Attempts one durable commit and returns the ordinary application outcome explicitly. */
  PostingCommitResult commit(PostingDraft postingDraft, PostingIdGenerator postingIdGenerator);

  /**
   * Commits one fully materialized posting fact.
   *
   * <p>This overload exists for fixture-oriented callers that already hold a durable postingId.
   * Production callers should prefer the draft-based overload so stores can allocate postingId only
   * after commit acceptance.
   */
  default PostingCommitResult commit(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return commit(
        new PostingDraft(
            postingFact.journalEntry(), postingFact.reversalReference(), postingFact.provenance()),
        postingFact::postingId);
  }

  @Override
  void close();
}
