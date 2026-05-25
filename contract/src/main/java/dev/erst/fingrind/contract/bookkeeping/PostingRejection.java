package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.ContractResponse;
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

/** Closed family of domain rejections that can refuse a posting request deterministically. */
public sealed interface PostingRejection
    permits PostingRejection.BookNotInitialized,
        PostingRejection.AccountStateViolations,
        PostingRejection.EntrySemanticsViolations,
        PostingRejection.DuplicateIdempotencyKey,
        PostingRejection.BookFunctionalCurrencyMismatch,
        PostingRejection.TransferredPeriodResultViolation,
        PostingRejection.OpeningBalanceWindowClosed,
        PostingRejection.OpeningBalanceTouchesNominalAccount,
        PostingRejection.ResultHoldingAccountReserved,
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
      permits PostingRejection.UnknownAccount,
          PostingRejection.InactiveAccount,
          PostingRejection.NonPostableAccount {}

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

  /** Stable structured entry-semantics issue emitted for one rejected typed entry. */
  record EntrySemanticsViolation(String code, @Nullable String field, String message) {
    public EntrySemanticsViolation {
      code = ContractDescriptorValidation.requireText(code, "code");
      field = ContractDescriptorValidation.requireOptionalText(field, "field");
      message = ContractDescriptorValidation.requireText(message, "message");
    }
  }

  /** Rejection for one typed entry whose own semantics are incompatible with the selected book. */
  record EntrySemanticsViolations(List<EntrySemanticsViolation> violations)
      implements PostingRejection {
    public EntrySemanticsViolations {
      violations = ContractDescriptorValidation.copyList(violations, "violations");
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

  /** Duplicate idempotency rejection for a book-local request identity that already exists. */
  record DuplicateIdempotencyKey() implements PostingRejection {}

  /** Rejection for a posting whose entry currency diverges from the book functional currency. */
  record BookFunctionalCurrencyMismatch(
      CurrencyUnit functionalCurrency, CurrencyUnit attemptedCurrency) implements PostingRejection {
    public BookFunctionalCurrencyMismatch {
      Objects.requireNonNull(functionalCurrency, "functionalCurrency");
      Objects.requireNonNull(attemptedCurrency, "attemptedCurrency");
    }
  }

  /** Rejection for a posting attempt whose effective date falls inside one transferred period. */
  record TransferredPeriodResultViolation(
      LocalDate transferredThroughEffectiveDate, LocalDate attemptedEffectiveDate)
      implements PostingRejection {
    public TransferredPeriodResultViolation {
      Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
      Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
    }
  }

  /** Rejection for an opening-balance posting after ordinary book activity has begun. */
  record OpeningBalanceWindowClosed(
      PostingKind firstBlockingPostingKind, LocalDate firstBlockingEffectiveDate)
      implements PostingRejection {
    public OpeningBalanceWindowClosed {
      Objects.requireNonNull(firstBlockingPostingKind, "firstBlockingPostingKind");
      Objects.requireNonNull(firstBlockingEffectiveDate, "firstBlockingEffectiveDate");
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

  /** Rejection for one direct posting that attempts to use the active result-holding account. */
  record ResultHoldingAccountReserved(AccountCode accountCode) implements PostingRejection {
    public ResultHoldingAccountReserved {
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

  /** Returns one typed-entry violation for an account whose declared type contradicts the entry. */
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
   * Returns one typed-entry violation for an account whose declared financial-position
   * classification contradicts the entry.
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

  /** Returns one typed-entry violation for evidence whose source-document type is not admitted. */
  static EntrySemanticsViolation sourceDocumentTypeNotAccepted(
      BookkeepingEntryKind entryKind,
      SourceDocumentType sourceDocumentType,
      List<String> acceptedTypes) {
    Objects.requireNonNull(entryKind, "entryKind");
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
    List<String> acceptedTypeValues =
        List.copyOf(Objects.requireNonNull(acceptedTypes, "acceptedTypes"));
    return new EntrySemanticsViolation(
        "source-document-type-not-accepted",
        "evidence.sourceDocuments[].sourceDocumentType",
        "Entry kind '%s' does not accept sourceDocumentType '%s'. Accepted values: %s."
            .formatted(
                entryKind.wireValue(),
                sourceDocumentType.value(),
                String.join(", ", acceptedTypeValues)));
  }
}
