package dev.erst.fingrind.executor.bookkeeping;

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

/** Local bookkeeping refusal family for posting validation and commit acceptance. */
public sealed interface BookkeepingPostingRejection
    permits FoundationalBookkeepingPostingRejection, WorkflowBookkeepingPostingRejection {

  /** Refusal for a posting request against a missing or uninitialized book. */
  record BookNotInitialized() implements FoundationalBookkeepingPostingRejection {}

  /** Closed family of account-state issues surfaced while validating one posting request. */
  sealed interface AccountStateViolation
      permits BookkeepingPostingRejection.UnknownAccount,
          BookkeepingPostingRejection.InactiveAccount,
          BookkeepingPostingRejection.NonPostableAccount,
          InventoryMovementPrecedesAccountHorizonViolation,
          InventoryQuantityBelowZeroViolation,
          InventoryWriteDownExceedsCarryingCostViolation {}

  /** Refusal for a posting request with one or more account-state violations. */
  record AccountStateViolations(List<AccountStateViolation> violations)
      implements FoundationalBookkeepingPostingRejection {
    public AccountStateViolations {
      violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
      if (violations.isEmpty()) {
        throw new IllegalArgumentException(
            "Posting account-state violations must contain at least one issue.");
      }
    }
  }

  /** Stable structured entry-semantics issue emitted for one rejected typed entry. */
  record EntrySemanticsViolation(String code, @Nullable String field, String message) {
    public EntrySemanticsViolation {
      if (code == null || code.isBlank()) {
        throw new IllegalArgumentException("Entry semantics violation code must not be blank.");
      }
      if (field != null && field.isBlank()) {
        throw new IllegalArgumentException(
            "Entry semantics violation field must not be blank when present.");
      }
      if (message == null || message.isBlank()) {
        throw new IllegalArgumentException("Entry semantics violation message must not be blank.");
      }
    }
  }

  /** Refusal for one typed entry whose own semantics are incompatible with the selected book. */
  record EntrySemanticsViolations(List<EntrySemanticsViolation> violations)
      implements FoundationalBookkeepingPostingRejection {
    public EntrySemanticsViolations {
      violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
      if (violations.isEmpty()) {
        throw new IllegalArgumentException(
            "Entry semantics violations must contain at least one issue.");
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

  /** One non-postable header account referenced by a posting request. */
  record NonPostableAccount(AccountCode accountCode, AccountNodeKind accountNodeKind)
      implements AccountStateViolation {
    public NonPostableAccount {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(accountNodeKind, "accountNodeKind");
    }
  }

  /** Refusal for one reused idempotency key whose semantic request fingerprint differs. */
  record IdempotencyKeyConflict() implements FoundationalBookkeepingPostingRejection {}

  /** Refusal for a posting attempt whose effective date falls after the current UTC date. */
  record PostingEffectiveDateInFuture(LocalDate attemptedEffectiveDate, LocalDate currentUtcDate)
      implements FoundationalBookkeepingPostingRejection {
    public PostingEffectiveDateInFuture {
      Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
      Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    }
  }

  /** Refusal for a posting whose entry currency diverges from the book functional currency. */
  record BookFunctionalCurrencyMismatch(
      CurrencyUnit functionalCurrency, CurrencyUnit attemptedCurrency)
      implements FoundationalBookkeepingPostingRejection {
    public BookFunctionalCurrencyMismatch {
      Objects.requireNonNull(functionalCurrency, "functionalCurrency");
      Objects.requireNonNull(attemptedCurrency, "attemptedCurrency");
    }
  }

  /** Refusal for a posting request whose effective date falls inside one transferred period. */
  record SweptInterimResultViolation(
      LocalDate transferredThroughEffectiveDate, LocalDate attemptedEffectiveDate)
      implements FoundationalBookkeepingPostingRejection {
    public SweptInterimResultViolation {
      Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
      Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
    }
  }

  /** Refusal for an OPENING_POSITION request after ordinary book activity has begun. */
  record OpeningPositionWindowClosed(
      PostingKind firstBlockingPostingKind, LocalDate firstBlockingEffectiveDate)
      implements WorkflowBookkeepingPostingRejection {
    public OpeningPositionWindowClosed {
      Objects.requireNonNull(firstBlockingPostingKind, "firstBlockingPostingKind");
      Objects.requireNonNull(firstBlockingEffectiveDate, "firstBlockingEffectiveDate");
    }
  }

  /** Refusal for an OPENING_POSITION request that touches nominal income-statement accounts. */
  record OpeningPositionTouchesNominalAccount(AccountCode accountCode, AccountType accountType)
      implements WorkflowBookkeepingPostingRejection {
    public OpeningPositionTouchesNominalAccount {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(accountType, "accountType");
    }
  }

  /** Refusal for one direct posting that attempts to use a close-reserved classification. */
  record ReservedResultClassification(
      AccountCode accountCode,
      FinancialPositionLineClassification financialPositionLineClassification)
      implements WorkflowBookkeepingPostingRejection {
    public ReservedResultClassification {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(
          financialPositionLineClassification, "financialPositionLineClassification");
    }
  }

  /** Refusal for a reversal whose referenced prior posting does not exist in this book. */
  record ReversalTargetNotFound(PostingId priorPostingId)
      implements WorkflowBookkeepingPostingRejection {
    public ReversalTargetNotFound {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }

  /** Refusal for a reversal attempt when the target already has a full reversal. */
  record ReversalAlreadyExists(PostingId priorPostingId)
      implements WorkflowBookkeepingPostingRejection {
    public ReversalAlreadyExists {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }

  /** Refusal for a reversal candidate whose journal lines do not negate the target posting. */
  record ReversalDoesNotNegateTarget(PostingId priorPostingId)
      implements WorkflowBookkeepingPostingRejection {
    public ReversalDoesNotNegateTarget {
      Objects.requireNonNull(priorPostingId, "priorPostingId");
    }
  }
}
