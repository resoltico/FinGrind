package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.List;
import java.util.Objects;

/** Local bookkeeping refusal family for posting validation and commit acceptance. */
public sealed interface BookkeepingPostingRejection
    permits BookkeepingPostingRejection.BookNotInitialized,
        BookkeepingPostingRejection.AccountStateViolations,
        BookkeepingPostingRejection.DuplicateIdempotencyKey,
        BookkeepingPostingRejection.ReversalTargetNotFound,
        BookkeepingPostingRejection.ReversalAlreadyExists,
        BookkeepingPostingRejection.ReversalDoesNotNegateTarget {

  /** Refusal for a posting request against a missing or uninitialized book. */
  record BookNotInitialized() implements BookkeepingPostingRejection {}

  /** Closed family of account-state issues surfaced while validating one posting request. */
  sealed interface AccountStateViolation
      permits BookkeepingPostingRejection.UnknownAccount,
          BookkeepingPostingRejection.InactiveAccount {}

  /** Refusal for a posting request with one or more account-state violations. */
  record AccountStateViolations(List<AccountStateViolation> violations)
      implements BookkeepingPostingRejection {
    public AccountStateViolations {
      violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
      if (violations.isEmpty()) {
        throw new IllegalArgumentException(
            "Posting account-state violations must contain at least one issue.");
      }
    }
  }

  /** One undeclared account referenced by a posting request. */
  record UnknownAccount(AccountCode accountCode) implements AccountStateViolation {
    public UnknownAccount {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** One inactive account referenced by a posting request. */
  record InactiveAccount(AccountCode accountCode) implements AccountStateViolation {
    public InactiveAccount {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Duplicate idempotency refusal for a book-local request identity that already exists. */
  record DuplicateIdempotencyKey() implements BookkeepingPostingRejection {}

  /** Refusal for a reversal whose referenced prior posting does not exist in this book. */
  record ReversalTargetNotFound(PostingId priorPostingId) implements BookkeepingPostingRejection {
    public ReversalTargetNotFound {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }

  /** Refusal for a reversal attempt when the target already has a full reversal. */
  record ReversalAlreadyExists(PostingId priorPostingId) implements BookkeepingPostingRejection {
    public ReversalAlreadyExists {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }

  /** Refusal for a reversal candidate whose journal lines do not negate the target posting. */
  record ReversalDoesNotNegateTarget(PostingId priorPostingId)
      implements BookkeepingPostingRejection {
    public ReversalDoesNotNegateTarget {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }
}
