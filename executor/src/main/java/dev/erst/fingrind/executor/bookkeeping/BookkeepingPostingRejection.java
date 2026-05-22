package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Local bookkeeping refusal family for posting validation and commit acceptance. */
public sealed interface BookkeepingPostingRejection
    permits BookkeepingPostingRejection.BookNotInitialized,
        BookkeepingPostingRejection.AccountStateViolations,
        BookkeepingPostingRejection.DuplicateIdempotencyKey,
        BookkeepingPostingRejection.PostingKindReserved,
        BookkeepingPostingRejection.BookFunctionalCurrencyMismatch,
        BookkeepingPostingRejection.ClosedPeriodViolation,
        BookkeepingPostingRejection.OpeningBalanceWindowClosed,
        BookkeepingPostingRejection.OpeningBalanceTouchesNominalAccount,
        BookkeepingPostingRejection.ClosingEquityAccountReserved,
        BookkeepingPostingRejection.ReversalTargetNotFound,
        BookkeepingPostingRejection.ReversalAlreadyExists,
        BookkeepingPostingRejection.ReversalDoesNotNegateTarget {

  /** Refusal for a posting request against a missing or uninitialized book. */
  record BookNotInitialized() implements BookkeepingPostingRejection {}

  /** Closed family of account-state issues surfaced while validating one posting request. */
  sealed interface AccountStateViolation
      permits BookkeepingPostingRejection.UnknownAccount,
          BookkeepingPostingRejection.InactiveAccount,
          BookkeepingPostingRejection.NonPostableAccount {}

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

  /** One non-postable header account referenced by a posting request. */
  record NonPostableAccount(AccountCode accountCode, AccountNodeKind accountNodeKind)
      implements AccountStateViolation {
    public NonPostableAccount {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(accountNodeKind, "accountNodeKind");
    }
  }

  /** Duplicate idempotency refusal for a book-local request identity that already exists. */
  record DuplicateIdempotencyKey() implements BookkeepingPostingRejection {}

  /** Refusal for a caller-authored posting that attempts to use a generated posting kind. */
  record PostingKindReserved(PostingKind postingKind) implements BookkeepingPostingRejection {
    public PostingKindReserved {
      Objects.requireNonNull(postingKind, "postingKind");
    }
  }

  /** Refusal for a posting whose entry currency diverges from the book functional currency. */
  record BookFunctionalCurrencyMismatch(
      CurrencyUnit functionalCurrency, CurrencyUnit attemptedCurrency)
      implements BookkeepingPostingRejection {
    public BookFunctionalCurrencyMismatch {
      Objects.requireNonNull(functionalCurrency, "functionalCurrency");
      Objects.requireNonNull(attemptedCurrency, "attemptedCurrency");
    }
  }

  /** Refusal for a posting request whose effective date falls inside one closed period. */
  record ClosedPeriodViolation(
      LocalDate closedThroughEffectiveDate, LocalDate attemptedEffectiveDate)
      implements BookkeepingPostingRejection {
    public ClosedPeriodViolation {
      Objects.requireNonNull(closedThroughEffectiveDate, "closedThroughEffectiveDate");
      Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
    }
  }

  /** Refusal for an opening-balance posting after ordinary book activity has begun. */
  record OpeningBalanceWindowClosed(
      PostingKind firstBlockingPostingKind, LocalDate firstBlockingEffectiveDate)
      implements BookkeepingPostingRejection {
    public OpeningBalanceWindowClosed {
      Objects.requireNonNull(firstBlockingPostingKind, "firstBlockingPostingKind");
      Objects.requireNonNull(firstBlockingEffectiveDate, "firstBlockingEffectiveDate");
    }
  }

  /** Refusal for an opening-balance posting that touches nominal income-statement accounts. */
  record OpeningBalanceTouchesNominalAccount(AccountCode accountCode, AccountType accountType)
      implements BookkeepingPostingRejection {
    public OpeningBalanceTouchesNominalAccount {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(accountType, "accountType");
    }
  }

  /** Refusal for one direct posting that attempts to use the closing-equity account. */
  record ClosingEquityAccountReserved(AccountCode accountCode)
      implements BookkeepingPostingRejection {
    public ClosingEquityAccountReserved {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

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
