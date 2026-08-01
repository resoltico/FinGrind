package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Closed family of domain rejections that can refuse a posting request deterministically. */
public sealed interface PostingRejection
    permits FoundationalPostingRejection, WorkflowPostingRejection {

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
  static List<RejectionDescriptor> descriptors() {
    return PostingRejectionDescriptors.descriptors();
  }

  /** Returns the stable structured detail payload for one account-state violation. */
  static AccountStateViolationDetail accountStateDetail(AccountStateViolation violation) {
    PostingRejection.AccountStateViolation requiredViolation =
        Objects.requireNonNull(violation, "violation");
    return new AccountStateViolationDetail(
        AccountStateViolationOwner.code(requiredViolation),
        AccountStateViolationOwner.field(requiredViolation),
        AccountStateViolationOwner.message(requiredViolation),
        AccountStateViolationOwner.category(requiredViolation),
        AccountStateViolationOwner.repair(requiredViolation),
        AccountStateViolationOwner.accountCode(requiredViolation).value(),
        AccountStateViolationOwner.accountNodeKind(requiredViolation));
  }

  /** Rejection for a posting request against a missing or uninitialized book. */
  record BookNotInitialized() implements FoundationalPostingRejection {}

  /** Closed family of account-state issues surfaced while validating one posting request. */
  sealed interface AccountStateViolation
      permits PostingRejection.UnknownAccount,
          PostingRejection.InactiveAccount,
          PostingRejection.NonPostableAccount,
          InventoryMovementPrecedesAccountHorizon,
          InventoryQuantityBelowZero,
          InventoryWriteDownExceedsCarryingCost {}

  /** Rejection for a posting request with one or more account-state violations. */
  record AccountStateViolations(List<AccountStateViolation> violations)
      implements FoundationalPostingRejection {
    /** Validates the account-state violation payload. */
    public AccountStateViolations {
      violations = AccountStateViolationOwner.inCanonicalOrder(violations);
      if (violations.isEmpty()) {
        throw new IllegalArgumentException(
            "Posting account-state violations must contain at least one issue.");
      }
    }
  }

  /** Stable structured entry-semantics issue emitted for one rejected typed entry. */
  record EntrySemanticsViolation(
      String code, @Nullable String field, String message, String category, String repair) {
    /** Creates one entry-semantics violation from the canonical code-owned metadata. */
    public EntrySemanticsViolation(String code, @Nullable String field, String message) {
      this(
          code,
          field,
          message,
          EntrySemanticsViolationOwner.require(code).category(),
          EntrySemanticsViolationOwner.require(code).repair());
    }

    public EntrySemanticsViolation {
      code = ContractDescriptorValidation.requireText(code, "code");
      field = ContractDescriptorValidation.requireOptionalText(field, "field");
      message = ContractDescriptorValidation.requireText(message, "message");
      category = ContractDescriptorValidation.requireText(category, "category");
      repair = ContractDescriptorValidation.requireText(repair, "repair");
      EntrySemanticsViolationOwner.validateKnownMetadata(code, category, repair);
    }
  }

  /** Rejection for one typed entry whose own semantics are incompatible with the selected book. */
  record EntrySemanticsViolations(List<EntrySemanticsViolation> violations)
      implements FoundationalPostingRejection {
    public EntrySemanticsViolations {
      violations = EntrySemanticsViolationOwner.inCanonicalOrder(violations);
      if (violations.isEmpty()) {
        throw new IllegalArgumentException(
            "Entry semantics violations must contain at least one issue.");
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

  /** One non-postable header account referenced by a posting request. */
  record NonPostableAccount(AccountCode accountCode, AccountNodeKind accountNodeKind)
      implements AccountStateViolation {
    public NonPostableAccount {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(accountNodeKind, "accountNodeKind");
    }
  }

  /** Rejection for one reused idempotency key whose semantic request differs. */
  record IdempotencyKeyConflict() implements FoundationalPostingRejection {}

  /** Rejection for a posting attempt whose effective date falls after the current UTC date. */
  record PostingEffectiveDateInFuture(LocalDate attemptedEffectiveDate, LocalDate currentUtcDate)
      implements FoundationalPostingRejection {
    public PostingEffectiveDateInFuture {
      Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
      Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    }
  }

  /** Rejection for a posting whose entry currency diverges from the book functional currency. */
  record BookFunctionalCurrencyMismatch(
      CurrencyUnit functionalCurrency, CurrencyUnit attemptedCurrency)
      implements FoundationalPostingRejection {
    public BookFunctionalCurrencyMismatch {
      Objects.requireNonNull(functionalCurrency, "functionalCurrency");
      Objects.requireNonNull(attemptedCurrency, "attemptedCurrency");
    }
  }

  /** Rejection for a posting attempt whose effective date falls inside one transferred period. */
  record SweptInterimResultViolation(
      LocalDate transferredThroughEffectiveDate, LocalDate attemptedEffectiveDate)
      implements FoundationalPostingRejection {
    public SweptInterimResultViolation {
      Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
      Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
    }
  }

  /** Rejection for an OPENING_POSITION request after ordinary book activity has begun. */
  record OpeningPositionWindowClosed(
      PostingKind firstBlockingPostingKind, LocalDate firstBlockingEffectiveDate)
      implements WorkflowPostingRejection {
    public OpeningPositionWindowClosed {
      Objects.requireNonNull(firstBlockingPostingKind, "firstBlockingPostingKind");
      Objects.requireNonNull(firstBlockingEffectiveDate, "firstBlockingEffectiveDate");
    }
  }

  /** Rejection for an OPENING_POSITION request that touches nominal income-statement accounts. */
  record OpeningPositionTouchesNominalAccount(AccountCode accountCode, AccountType accountType)
      implements WorkflowPostingRejection {
    public OpeningPositionTouchesNominalAccount {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(accountType, "accountType");
    }
  }

  /** Rejection for one direct posting that attempts to use a close-reserved classification. */
  record ReservedResultClassification(
      AccountCode accountCode,
      FinancialPositionLineClassification financialPositionLineClassification)
      implements WorkflowPostingRejection {
    public ReservedResultClassification {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(
          financialPositionLineClassification, "financialPositionLineClassification");
    }
  }

  /** Rejection for a reversal whose referenced prior posting does not exist in this book. */
  record ReversalTargetNotFound(PostingId priorPostingId) implements WorkflowPostingRejection {
    /** Validates the missing reversal target descriptor. */
    public ReversalTargetNotFound {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }

  /** Rejection for a reversal attempt when the target already has a full reversal. */
  record ReversalAlreadyExists(PostingId priorPostingId) implements WorkflowPostingRejection {
    /** Validates the reversal-target descriptor. */
    public ReversalAlreadyExists {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }

  /** Rejection for a reversal candidate whose journal lines do not negate the target posting. */
  record ReversalDoesNotNegateTarget(PostingId priorPostingId) implements WorkflowPostingRejection {
    /** Validates the reversal-mismatch descriptor. */
    public ReversalDoesNotNegateTarget {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }
}
