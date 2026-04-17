package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.List;
import java.util.Objects;

/** Closed family of domain rejections that can refuse a posting request deterministically. */
public sealed interface PostingRejection
    permits PostingRejection.BookNotInitialized,
        PostingRejection.AccountStateViolations,
        PostingRejection.DuplicateIdempotencyKey,
        PostingRejection.ReversalReasonForbidden,
        PostingRejection.ReversalReasonRequired,
        PostingRejection.ReversalTargetNotFound,
        PostingRejection.ReversalAlreadyExists,
        PostingRejection.ReversalDoesNotNegateTarget {

  /** Returns the stable wire code for one posting rejection instance. */
  static String wireCode(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ -> "book-not-initialized";
      case PostingRejection.AccountStateViolations _ -> "account-state-violations";
      case PostingRejection.DuplicateIdempotencyKey _ -> "duplicate-idempotency-key";
      case PostingRejection.ReversalReasonRequired _ -> "reversal-reason-required";
      case PostingRejection.ReversalReasonForbidden _ -> "reversal-reason-forbidden";
      case PostingRejection.ReversalTargetNotFound _ -> "reversal-target-not-found";
      case PostingRejection.ReversalAlreadyExists _ -> "reversal-already-exists";
      case PostingRejection.ReversalDoesNotNegateTarget _ -> "reversal-does-not-negate-target";
    };
  }

  /** Returns the canonical machine descriptors for every permitted posting rejection subtype. */
  static List<MachineContract.RejectionDescriptor> descriptors() {
    return List.of(
        new MachineContract.RejectionDescriptor(
            "book-not-initialized",
            "Posting refused because the selected book does not exist or has not been initialized with "
                + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                + "."),
        new MachineContract.RejectionDescriptor(
            "account-state-violations",
            "Posting refused because one or more journal lines reference undeclared or inactive accounts."),
        new MachineContract.RejectionDescriptor(
            "duplicate-idempotency-key",
            "Posting refused because the selected book already contains the same idempotency key."),
        new MachineContract.RejectionDescriptor(
            "reversal-reason-required",
            "Posting refused because reversal requests must carry a human-readable provenance.reason."),
        new MachineContract.RejectionDescriptor(
            "reversal-reason-forbidden",
            "Posting refused because provenance.reason is only accepted when reversal is present."),
        new MachineContract.RejectionDescriptor(
            "reversal-target-not-found",
            "Posting refused because reversal.priorPostingId does not identify a committed posting in this book."),
        new MachineContract.RejectionDescriptor(
            "reversal-already-exists",
            "Posting refused because the selected prior posting already has a full reversal."),
        new MachineContract.RejectionDescriptor(
            "reversal-does-not-negate-target",
            "Posting refused because the candidate reversal does not exactly negate the target posting."));
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
      violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
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
