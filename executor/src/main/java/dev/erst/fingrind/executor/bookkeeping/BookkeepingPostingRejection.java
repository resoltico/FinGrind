package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.SourceDocumentType;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Local bookkeeping refusal family for posting validation and commit acceptance. */
public sealed interface BookkeepingPostingRejection
    permits BookkeepingPostingRejection.BookNotInitialized,
        BookkeepingPostingRejection.AccountStateViolations,
        BookkeepingPostingRejection.EntrySemanticsViolations,
        BookkeepingPostingRejection.DuplicateIdempotencyKey,
        BookkeepingPostingRejection.BookFunctionalCurrencyMismatch,
        BookkeepingPostingRejection.TransferredPeriodResultViolation,
        BookkeepingPostingRejection.OpeningBalanceWindowClosed,
        BookkeepingPostingRejection.OpeningBalanceTouchesNominalAccount,
        BookkeepingPostingRejection.ResultHoldingAccountReserved,
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
      implements BookkeepingPostingRejection {
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

  /** Duplicate idempotency refusal for a book-local request identity that already exists. */
  record DuplicateIdempotencyKey() implements BookkeepingPostingRejection {}

  /** Refusal for a posting whose entry currency diverges from the book functional currency. */
  record BookFunctionalCurrencyMismatch(
      CurrencyUnit functionalCurrency, CurrencyUnit attemptedCurrency)
      implements BookkeepingPostingRejection {
    public BookFunctionalCurrencyMismatch {
      Objects.requireNonNull(functionalCurrency, "functionalCurrency");
      Objects.requireNonNull(attemptedCurrency, "attemptedCurrency");
    }
  }

  /** Refusal for a posting request whose effective date falls inside one transferred period. */
  record TransferredPeriodResultViolation(
      LocalDate transferredThroughEffectiveDate, LocalDate attemptedEffectiveDate)
      implements BookkeepingPostingRejection {
    public TransferredPeriodResultViolation {
      Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
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

  /** Refusal for one direct posting that attempts to use the result-holding account. */
  record ResultHoldingAccountReserved(AccountCode accountCode)
      implements BookkeepingPostingRejection {
    public ResultHoldingAccountReserved {
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

  /** Creates one entry-semantics violation for a typed entry using the wrong account type. */
  static EntrySemanticsViolation accountTypeMismatch(
      BookkeepingEntryKind entryKind,
      String field,
      AccountCode accountCode,
      AccountType expectedAccountType,
      AccountType actualAccountType) {
    Objects.requireNonNull(entryKind, "entryKind");
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedAccountType, "expectedAccountType");
    Objects.requireNonNull(actualAccountType, "actualAccountType");
    return new EntrySemanticsViolation(
        "account-type-mismatch",
        field,
        "Entry kind '%s' requires %s '%s' to be account type '%s', but the declared account type is '%s'."
            .formatted(
                entryKind.wireValue(),
                field,
                accountCode.value(),
                expectedAccountType.wireValue(),
                actualAccountType.wireValue()));
  }

  /**
   * Creates one entry-semantics violation for a typed entry using the wrong financial-position
   * classification.
   */
  static EntrySemanticsViolation financialPositionClassificationMismatch(
      BookkeepingEntryKind entryKind,
      String field,
      AccountCode accountCode,
      FinancialPositionLineClassification expectedClassification,
      @Nullable FinancialPositionLineClassification actualClassification) {
    Objects.requireNonNull(entryKind, "entryKind");
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedClassification, "expectedClassification");
    return new EntrySemanticsViolation(
        "financial-position-classification-mismatch",
        field,
        "Entry kind '%s' requires %s '%s' to use financialPositionLineClassification '%s', but the declared account uses '%s'."
            .formatted(
                entryKind.wireValue(),
                field,
                accountCode.value(),
                expectedClassification.wireValue(),
                actualClassification == null ? "<absent>" : actualClassification.wireValue()));
  }

  /** Creates one entry-semantics violation for a typed entry using an unsupported evidence type. */
  static EntrySemanticsViolation sourceDocumentTypeNotAccepted(
      BookkeepingEntryKind entryKind,
      SourceDocumentType sourceDocumentType,
      List<String> acceptedTypes) {
    Objects.requireNonNull(entryKind, "entryKind");
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
    List<String> acceptedDocumentTypes =
        List.copyOf(Objects.requireNonNull(acceptedTypes, "acceptedTypes"));
    return new EntrySemanticsViolation(
        "source-document-type-not-accepted",
        "evidence.sourceDocuments[].sourceDocumentType",
        "Entry kind '%s' does not accept sourceDocumentType '%s'. Accepted values: %s."
            .formatted(
                entryKind.wireValue(),
                sourceDocumentType.value(),
                String.join(", ", acceptedDocumentTypes)));
  }
}
