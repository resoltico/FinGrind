package dev.erst.fingrind.application;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Closed family of domain rejections that can refuse a posting request deterministically. */
public sealed interface PostingRejection
    permits PostingRejection.BookNotInitialized,
        PostingRejection.UnknownAccount,
        PostingRejection.InactiveAccount,
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
      case PostingRejection.UnknownAccount _ -> "unknown-account";
      case PostingRejection.InactiveAccount _ -> "inactive-account";
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
    return Arrays.stream(PostingRejection.class.getPermittedSubclasses())
        .map(type -> descriptorFor(type.asSubclass(PostingRejection.class)))
        .toList();
  }

  private static MachineContract.RejectionDescriptor descriptorFor(
      Class<? extends PostingRejection> rejectionType) {
    if (rejectionType == PostingRejection.BookNotInitialized.class) {
      return new MachineContract.RejectionDescriptor(
          "book-not-initialized",
          "Posting refused because the selected book does not exist or has not been initialized with open-book.");
    }
    if (rejectionType == PostingRejection.UnknownAccount.class) {
      return new MachineContract.RejectionDescriptor(
          "unknown-account",
          "Posting refused because at least one journal line references an undeclared account.");
    }
    if (rejectionType == PostingRejection.InactiveAccount.class) {
      return new MachineContract.RejectionDescriptor(
          "inactive-account",
          "Posting refused because at least one journal line references an inactive account.");
    }
    if (rejectionType == PostingRejection.DuplicateIdempotencyKey.class) {
      return new MachineContract.RejectionDescriptor(
          "duplicate-idempotency-key",
          "Posting refused because the selected book already contains the same idempotency key.");
    }
    if (rejectionType == PostingRejection.ReversalReasonRequired.class) {
      return new MachineContract.RejectionDescriptor(
          "reversal-reason-required",
          "Posting refused because reversal requests must carry a human-readable provenance.reason.");
    }
    if (rejectionType == PostingRejection.ReversalReasonForbidden.class) {
      return new MachineContract.RejectionDescriptor(
          "reversal-reason-forbidden",
          "Posting refused because provenance.reason is only accepted when reversal is present.");
    }
    if (rejectionType == PostingRejection.ReversalTargetNotFound.class) {
      return new MachineContract.RejectionDescriptor(
          "reversal-target-not-found",
          "Posting refused because reversal.priorPostingId does not identify a committed posting in this book.");
    }
    if (rejectionType == PostingRejection.ReversalAlreadyExists.class) {
      return new MachineContract.RejectionDescriptor(
          "reversal-already-exists",
          "Posting refused because the selected prior posting already has a full reversal.");
    }
    if (rejectionType == PostingRejection.ReversalDoesNotNegateTarget.class) {
      return new MachineContract.RejectionDescriptor(
          "reversal-does-not-negate-target",
          "Posting refused because the candidate reversal does not exactly negate the target posting.");
    }
    throw new IllegalStateException(
        "Unsupported posting rejection type: " + rejectionType.getName());
  }

  /** Rejection for a posting request against a missing or uninitialized book. */
  record BookNotInitialized() implements PostingRejection {}

  /** Rejection for a posting request that references an undeclared account. */
  record UnknownAccount(AccountCode accountCode) implements PostingRejection {
    /** Validates the missing account descriptor. */
    public UnknownAccount {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Rejection for a posting request that references an inactive account. */
  record InactiveAccount(AccountCode accountCode) implements PostingRejection {
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
