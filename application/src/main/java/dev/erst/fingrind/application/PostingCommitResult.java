package dev.erst.fingrind.application;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;

/** Closed family of ordinary commit outcomes returned by the book-session seam. */
public sealed interface PostingCommitResult
    permits PostingCommitResult.Committed,
        PostingCommitResult.BookNotInitialized,
        PostingCommitResult.UnknownAccount,
        PostingCommitResult.InactiveAccount,
        PostingCommitResult.DuplicateIdempotency,
        PostingCommitResult.DuplicateReversalTarget {

  /** Successful durable commit outcome carrying the stored posting fact. */
  record Committed(PostingFact postingFact) implements PostingCommitResult {
    /** Validates the committed posting result. */
    public Committed {
      Objects.requireNonNull(postingFact, "postingFact");
    }
  }

  /** Commit outcome indicating that the selected book was not explicitly initialized. */
  record BookNotInitialized() implements PostingCommitResult {}

  /** Commit outcome indicating that one referenced account is not declared in this book. */
  record UnknownAccount(AccountCode accountCode) implements PostingCommitResult {
    /** Validates the undeclared-account outcome. */
    public UnknownAccount {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Commit outcome indicating that one referenced account exists but is inactive. */
  record InactiveAccount(AccountCode accountCode) implements PostingCommitResult {
    /** Validates the inactive-account outcome. */
    public InactiveAccount {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Commit outcome indicating that the book already contains the idempotency identity. */
  record DuplicateIdempotency(IdempotencyKey idempotencyKey) implements PostingCommitResult {
    /** Validates the duplicate-idempotency outcome. */
    public DuplicateIdempotency {
      Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }
  }

  /** Commit outcome indicating that the target posting already has a full reversal. */
  record DuplicateReversalTarget(PostingId priorPostingId) implements PostingCommitResult {
    /** Validates the duplicate-reversal outcome. */
    public DuplicateReversalTarget {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }
}
