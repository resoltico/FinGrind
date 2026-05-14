package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Closed family of domain rejections that can refuse a posting request deterministically. */
public sealed interface PostingRejection
    permits PostingRejection.BookNotInitialized,
        PostingRejection.AccountStateViolations,
        PostingRejection.DuplicateIdempotencyKey,
        PostingRejection.PostingKindReserved,
        PostingRejection.BookFunctionalCurrencyMismatch,
        PostingRejection.ClosedPeriodViolation,
        PostingRejection.OpeningBalanceTouchesNominalAccount,
        PostingRejection.RetainedEarningsAccountReserved,
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
      violations = ContractDescriptorValidation.copyList(violations, "violations");
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

  /** Rejection for a caller-authored posting that attempts to use a generated posting kind. */
  record PostingKindReserved(PostingKind postingKind) implements PostingRejection {
    public PostingKindReserved {
      Objects.requireNonNull(postingKind, "postingKind");
    }
  }

  /** Rejection for a posting whose entry currency diverges from the book functional currency. */
  record BookFunctionalCurrencyMismatch(
      CurrencyUnit functionalCurrency, CurrencyUnit attemptedCurrency) implements PostingRejection {
    public BookFunctionalCurrencyMismatch {
      Objects.requireNonNull(functionalCurrency, "functionalCurrency");
      Objects.requireNonNull(attemptedCurrency, "attemptedCurrency");
    }
  }

  /** Rejection for a posting attempt whose effective date falls inside one closed period. */
  record ClosedPeriodViolation(
      LocalDate closedThroughEffectiveDate, LocalDate attemptedEffectiveDate)
      implements PostingRejection {
    public ClosedPeriodViolation {
      Objects.requireNonNull(closedThroughEffectiveDate, "closedThroughEffectiveDate");
      Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
    }
  }

  /** Rejection for an opening-balance posting that touches nominal income-statement accounts. */
  record OpeningBalanceTouchesNominalAccount(AccountCode accountCode, AccountType accountType)
      implements PostingRejection {
    public OpeningBalanceTouchesNominalAccount {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(accountType, "accountType");
    }
  }

  /** Rejection for one direct posting that attempts to use the retained-earnings account. */
  record RetainedEarningsAccountReserved(AccountCode accountCode) implements PostingRejection {
    public RetainedEarningsAccountReserved {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

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
