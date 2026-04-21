package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.List;
import java.util.Objects;

/** Closed family of domain rejections that can refuse a posting request deterministically. */
public sealed interface PostingRejection
    permits PostingRejection.BookNotInitialized,
        PostingRejection.AccountStateViolations,
        PostingRejection.DuplicateIdempotencyKey,
        PostingRejection.ReversalTargetNotFound,
        PostingRejection.ReversalAlreadyExists,
        PostingRejection.ReversalDoesNotNegateTarget {

  /** Returns the stable wire code for one posting rejection instance. */
  static String wireCode(PostingRejection rejection) {
    return PostingRejectionDescriptors.wireCode(rejection);
  }

  /** Returns the stable wire code for the missing-book posting rejection. */
  static String bookNotInitializedCode() {
    return PostingRejectionDescriptors.bookNotInitializedCode();
  }

  /** Returns the stable wire code for one account-state violation detail. */
  static String wireCode(AccountStateViolation violation) {
    return PostingRejectionDescriptors.wireCode(violation);
  }

  /** Returns the canonical machine descriptors for every permitted posting rejection subtype. */
  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return PostingRejectionDescriptors.descriptors();
  }

  /** Rejection for a posting request against a missing or uninitialized book. */
  record BookNotInitialized() implements PostingRejection {}

  /** Closed family of account-state issues surfaced while validating one posting request. */
  sealed interface AccountStateViolation
      permits PostingRejection.UnknownAccount, PostingRejection.InactiveAccount {}

  /** Rejection for a posting request with one or more account-state violations. */
  record AccountStateViolations(List<AccountStateViolation> violations)
      implements PostingRejection {
    /** Validates the account-state violation payload. */
    public AccountStateViolations {
      violations = violations == null ? List.of() : List.copyOf(violations);
      if (violations.isEmpty()) {
        throw new IllegalArgumentException(
            "Posting account-state violations must contain at least one issue.");
      }
    }
  }

  /** One undeclared account referenced by a posting request. */
  record UnknownAccount(AccountCode accountCode) implements AccountStateViolation {
    /** Validates the missing account descriptor. */
    public UnknownAccount {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** One inactive account referenced by a posting request. */
  record InactiveAccount(AccountCode accountCode) implements AccountStateViolation {
    /** Validates the inactive account descriptor. */
    public InactiveAccount {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Duplicate idempotency rejection for a book-local request identity that already exists. */
  record DuplicateIdempotencyKey() implements PostingRejection {}

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
