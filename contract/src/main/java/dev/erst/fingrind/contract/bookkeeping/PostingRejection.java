package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.SourceDocumentType;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Closed family of domain rejections that can refuse a posting request deterministically. */
public sealed interface PostingRejection
    permits PostingRejection.BookNotInitialized,
        PostingRejection.AccountStateViolations,
        PostingRejection.EntrySemanticsViolations,
        PostingRejection.DuplicateIdempotencyKey,
        PostingRejection.BookFunctionalCurrencyMismatch,
        PostingRejection.TransferredPeriodResultViolation,
        PostingRejection.OpenAccountingPositionWindowClosed,
        PostingRejection.OpenAccountingPositionTouchesNominalAccount,
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

  /** Rejection for an OPEN_ACCOUNTING_POSITION request after ordinary book activity has begun. */
  record OpenAccountingPositionWindowClosed(
      PostingKind firstBlockingPostingKind, LocalDate firstBlockingEffectiveDate)
      implements PostingRejection {
    public OpenAccountingPositionWindowClosed {
      Objects.requireNonNull(firstBlockingPostingKind, "firstBlockingPostingKind");
      Objects.requireNonNull(firstBlockingEffectiveDate, "firstBlockingEffectiveDate");
    }
  }

  /**
   * Rejection for an OPEN_ACCOUNTING_POSITION request that touches nominal income-statement
   * accounts.
   */
  record OpenAccountingPositionTouchesNominalAccount(
      AccountCode accountCode, AccountType accountType) implements PostingRejection {
    public OpenAccountingPositionTouchesNominalAccount {
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

  /**
   * Returns one entry-semantics violation for an account whose declared type contradicts the
   * request.
   */
  static EntrySemanticsViolation accountTypeMismatch(
      String entryLabel,
      String field,
      AccountCode accountCode,
      AccountType expectedAccountType,
      AccountType actualAccountType) {
    String requiredEntryLabel = ContractDescriptorValidation.requireText(entryLabel, "entryLabel");
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedAccountType, "expectedAccountType");
    Objects.requireNonNull(actualAccountType, "actualAccountType");
    return new EntrySemanticsViolation(
        "account-type-mismatch",
        field,
        "Entry kind '%s' requires %s '%s' to be account type '%s', but the declared account type is '%s'."
            .formatted(
                requiredEntryLabel,
                field,
                accountCode.value(),
                expectedAccountType.wireValue(),
                actualAccountType.wireValue()));
  }

  /**
   * Returns one entry-semantics violation for an account whose declared financial-position
   * classification contradicts the entry.
   */
  static EntrySemanticsViolation financialPositionClassificationMismatch(
      String entryLabel,
      String field,
      AccountCode accountCode,
      FinancialPositionLineClassification expectedClassification,
      @Nullable FinancialPositionLineClassification actualClassification) {
    String requiredEntryLabel = ContractDescriptorValidation.requireText(entryLabel, "entryLabel");
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedClassification, "expectedClassification");
    return new EntrySemanticsViolation(
        "financial-position-classification-mismatch",
        field,
        "Entry kind '%s' requires %s '%s' to use financialPositionLineClassification '%s', but the declared account uses '%s'."
            .formatted(
                requiredEntryLabel,
                field,
                accountCode.value(),
                expectedClassification.wireValue(),
                actualClassification == null ? "<absent>" : actualClassification.wireValue()));
  }

  /**
   * Returns one entry-semantics violation for evidence whose source-document type is not admitted.
   */
  static EntrySemanticsViolation sourceDocumentTypeNotAccepted(
      String entryLabel, SourceDocumentType sourceDocumentType, List<String> acceptedTypes) {
    String requiredEntryLabel = ContractDescriptorValidation.requireText(entryLabel, "entryLabel");
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
    List<String> acceptedTypeValues =
        List.copyOf(Objects.requireNonNull(acceptedTypes, "acceptedTypes"));
    return new EntrySemanticsViolation(
        "source-document-type-not-accepted",
        "evidence.sourceDocuments[].sourceDocumentType",
        "Entry kind '%s' does not accept sourceDocumentType '%s'. Accepted values: %s."
            .formatted(
                requiredEntryLabel,
                sourceDocumentType.value(),
                String.join(", ", acceptedTypeValues)));
  }

  /** Returns one entry-semantics violation when two semantic roles collapse onto one account. */
  static EntrySemanticsViolation distinctRoleAccountsRequired(
      String entryLabel, String firstField, String secondField, AccountCode accountCode) {
    String requiredEntryLabel = ContractDescriptorValidation.requireText(entryLabel, "entryLabel");
    Objects.requireNonNull(firstField, "firstField");
    Objects.requireNonNull(secondField, "secondField");
    Objects.requireNonNull(accountCode, "accountCode");
    return new EntrySemanticsViolation(
        "distinct-role-accounts-required",
        null,
        "Entry kind '%s' requires %s and %s to reference distinct accounts, but both point to '%s'."
            .formatted(requiredEntryLabel, firstField, secondField, accountCode.value()));
  }

  /** Returns one insertion-ordered set of referenced accounts without rejecting duplicates. */
  static Set<AccountCode> referencedAccountSet(AccountCode... accountCodes) {
    Objects.requireNonNull(accountCodes, "accountCodes");
    Set<AccountCode> referencedAccounts = new LinkedHashSet<>();
    for (AccountCode accountCode : accountCodes) {
      referencedAccounts.add(Objects.requireNonNull(accountCode, "accountCode"));
    }
    return referencedAccounts;
  }
}
