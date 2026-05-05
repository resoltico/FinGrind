package dev.erst.fingrind.executor;

import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Objects;

/** Posting-only seam over an already-open book boundary; lifecycle stays with the owner. */
public interface PostingBookSession extends PostingValidationBook {
  /** Attempts one durable commit and returns the ordinary application outcome explicitly. */
  PostingCommitResult commit(PostingDraft postingDraft, PostingIdGenerator postingIdGenerator);

  /**
   * Commits one fully materialized posting fact.
   *
   * <p>This overload exists for fixture-oriented callers that already hold a durable postingId.
   * Production callers should prefer the draft-based overload so stores can allocate postingId only
   * after commit acceptance.
   */
  default PostingCommitResult commit(CommittedPosting postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return commit(
        new PostingDraft(
            postingFact.journalEntry(), postingFact.postingLineage(), postingFact.provenance()),
        postingFact::postingId);
  }
}
