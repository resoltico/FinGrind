package dev.erst.fingrind.application;

import dev.erst.fingrind.core.PostingId;
import java.util.Objects;

/** Closed family of domain rejections that can refuse a posting request deterministically. */
public sealed interface PostingRejection
    permits PostingRejection.DuplicateIdempotencyKey,
        PostingRejection.ReversalReasonForbidden,
        PostingRejection.ReversalReasonRequired,
        PostingRejection.ReversalTargetNotFound,
        PostingRejection.ReversalAlreadyExists,
        PostingRejection.ReversalDoesNotNegateTarget {

  /** Duplicate idempotency rejection for a book-local request identity that already exists. */
  record DuplicateIdempotencyKey() implements PostingRejection {}

  /** Rejection for a reversal posting that omitted the required human-readable reason. */
  record ReversalReasonRequired() implements PostingRejection {}

  /** Rejection for a non-reversal posting that supplied a reversal reason anyway. */
  record ReversalReasonForbidden() implements PostingRejection {}

  /** Rejection for a reversal whose referenced prior posting does not exist in this book. */
  record ReversalTargetNotFound(PostingId priorPostingId) implements PostingRejection {
    /** Validates the missing reversal target descriptor. */
    public ReversalTargetNotFound {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }

  /** Rejection for a reversal attempt when the target already has a full reversal. */
  record ReversalAlreadyExists(PostingId priorPostingId) implements PostingRejection {
    /** Validates the reversal-target descriptor. */
    public ReversalAlreadyExists {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }

  /** Rejection for a reversal candidate whose journal lines do not negate the target posting. */
  record ReversalDoesNotNegateTarget(PostingId priorPostingId) implements PostingRejection {
    /** Validates the reversal-mismatch descriptor. */
    public ReversalDoesNotNegateTarget {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }
}
