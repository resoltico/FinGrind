package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;
import java.util.Objects;

/** Canonical descriptor ownership for posting-side deterministic rejections. */
final class PostingRejectionDescriptors {
  private PostingRejectionDescriptors() {}

  static String wireCode(PostingRejection rejection) {
    return descriptorFor(rejection).code();
  }

  static String bookNotInitializedCode() {
    return Descriptor.BOOK_NOT_INITIALIZED.code();
  }

  static String wireCode(PostingRejection.AccountStateViolation violation) {
    return detailDescriptorFor(violation).code();
  }

  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(name, description);
  }

  private static Descriptor descriptorFor(PostingRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case PostingRejection.BookNotInitialized _ -> Descriptor.BOOK_NOT_INITIALIZED;
      case PostingRejection.AccountStateViolations _ -> Descriptor.ACCOUNT_STATE_VIOLATIONS;
      case PostingRejection.EntrySemanticsViolations _ -> Descriptor.ENTRY_SEMANTICS_VIOLATIONS;
      case PostingRejection.DuplicateIdempotencyKey _ -> Descriptor.DUPLICATE_IDEMPOTENCY_KEY;
      case PostingRejection.BookFunctionalCurrencyMismatch _ ->
          Descriptor.BOOK_FUNCTIONAL_CURRENCY_MISMATCH;
      case PostingRejection.TransferredPeriodResultViolation _ ->
          Descriptor.CLOSED_PERIOD_VIOLATION;
      case PostingRejection.OpeningBalanceWindowClosed _ ->
          Descriptor.OPENING_BALANCE_WINDOW_CLOSED;
      case PostingRejection.OpeningBalanceTouchesNominalAccount _ ->
          Descriptor.OPENING_BALANCE_TOUCHES_NOMINAL_ACCOUNT;
      case PostingRejection.ResultHoldingAccountReserved _ ->
          Descriptor.RESULT_HOLDING_ACCOUNT_RESERVED;
      case PostingRejection.ReversalTargetNotFound _ -> Descriptor.REVERSAL_TARGET_NOT_FOUND;
      case PostingRejection.ReversalAlreadyExists _ -> Descriptor.REVERSAL_ALREADY_EXISTS;
      case PostingRejection.ReversalDoesNotNegateTarget _ ->
          Descriptor.REVERSAL_DOES_NOT_NEGATE_TARGET;
    };
  }

  private static AccountStateDetailDescriptor detailDescriptorFor(
      PostingRejection.AccountStateViolation violation) {
    return switch (Objects.requireNonNull(violation, "violation")) {
      case PostingRejection.UnknownAccount _ -> AccountStateDetailDescriptor.UNKNOWN_ACCOUNT;
      case PostingRejection.InactiveAccount _ -> AccountStateDetailDescriptor.INACTIVE_ACCOUNT;
      case PostingRejection.NonPostableAccount _ ->
          AccountStateDetailDescriptor.NON_POSTABLE_ACCOUNT;
    };
  }

  /** Canonical posting rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_NOT_INITIALIZED,
    ACCOUNT_STATE_VIOLATIONS,
    ENTRY_SEMANTICS_VIOLATIONS,
    DUPLICATE_IDEMPOTENCY_KEY,
    BOOK_FUNCTIONAL_CURRENCY_MISMATCH,
    CLOSED_PERIOD_VIOLATION,
    OPENING_BALANCE_WINDOW_CLOSED,
    OPENING_BALANCE_TOUCHES_NOMINAL_ACCOUNT,
    RESULT_HOLDING_ACCOUNT_RESERVED,
    REVERSAL_TARGET_NOT_FOUND,
    REVERSAL_ALREADY_EXISTS,
    REVERSAL_DOES_NOT_NEGATE_TARGET;

    private String code() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED -> "posting-book-not-initialized";
        case ACCOUNT_STATE_VIOLATIONS -> "account-state-violations";
        case ENTRY_SEMANTICS_VIOLATIONS -> "entry-semantics-violations";
        case DUPLICATE_IDEMPOTENCY_KEY -> "duplicate-idempotency-key";
        case BOOK_FUNCTIONAL_CURRENCY_MISMATCH -> "book-functional-currency-mismatch";
        case CLOSED_PERIOD_VIOLATION -> "closed-period-violation";
        case OPENING_BALANCE_WINDOW_CLOSED -> "opening-balance-window-closed";
        case OPENING_BALANCE_TOUCHES_NOMINAL_ACCOUNT -> "opening-balance-touches-nominal-account";
        case RESULT_HOLDING_ACCOUNT_RESERVED -> "result-holding-account-reserved";
        case REVERSAL_TARGET_NOT_FOUND -> "reversal-target-not-found";
        case REVERSAL_ALREADY_EXISTS -> "reversal-already-exists";
        case REVERSAL_DOES_NOT_NEGATE_TARGET -> "reversal-does-not-negate-target";
      };
    }

    private String description() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED ->
            "Posting refused because the selected book does not exist or has not been initialized with "
                + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                + ".";
        case ACCOUNT_STATE_VIOLATIONS ->
            "Posting refused because one or more journal lines reference undeclared or inactive accounts.";
        case ENTRY_SEMANTICS_VIOLATIONS ->
            "Posting refused because the selected typed entry contradicts its own published accounting semantics.";
        case DUPLICATE_IDEMPOTENCY_KEY ->
            "Posting refused because the selected book already contains the same idempotency key.";
        case BOOK_FUNCTIONAL_CURRENCY_MISMATCH ->
            "Posting refused because the journal-entry currency does not match the selected book functional currency.";
        case CLOSED_PERIOD_VIOLATION ->
            "Posting refused because its effective date falls inside one transferred reporting period.";
        case OPENING_BALANCE_WINDOW_CLOSED ->
            "Posting refused because opening-balance entries are allowed only before the first committed posting in the selected book.";
        case OPENING_BALANCE_TOUCHES_NOMINAL_ACCOUNT ->
            "Posting refused because opening-balance entries may seed only asset, liability, or equity accounts.";
        case RESULT_HOLDING_ACCOUNT_RESERVED ->
            "Posting refused because the result-holding account is reserved for generated period-result-transfer postings.";
        case REVERSAL_TARGET_NOT_FOUND ->
            "Posting refused because reversal.priorPostingId does not identify a committed posting in this book.";
        case REVERSAL_ALREADY_EXISTS ->
            "Posting refused because the selected prior posting already has a full reversal.";
        case REVERSAL_DOES_NOT_NEGATE_TARGET ->
            "Posting refused because the candidate reversal does not exactly negate the target posting.";
      };
    }

    private List<ContractResponse.FieldDescriptor> detailFields() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED, DUPLICATE_IDEMPOTENCY_KEY -> List.of();
        case ACCOUNT_STATE_VIOLATIONS ->
            List.of(
                detailField(
                    "violations",
                    "Array of per-line account-state issue objects with stable code and accountCode."));
        case ENTRY_SEMANTICS_VIOLATIONS ->
            List.of(
                detailField(
                    "violations",
                    "Array of typed-entry semantic issue objects with stable code, field, and message."));
        case BOOK_FUNCTIONAL_CURRENCY_MISMATCH ->
            List.of(
                detailField(
                    "functionalCurrency", "Functional currency declared by the selected book."),
                detailField(
                    "attemptedCurrency",
                    "Rejected journal-entry currency from the posting request."));
        case CLOSED_PERIOD_VIOLATION ->
            List.of(
                detailField(
                    "transferredThroughEffectiveDate",
                    "Inclusive effective date through which postings are already closed."),
                detailField(
                    "attemptedEffectiveDate", "Rejected effective date from the posting request."));
        case OPENING_BALANCE_WINDOW_CLOSED ->
            List.of(
                detailField(
                    "firstBlockingPostingKind",
                    "Previously committed posting kind that closed the one-time opening-balance admission window."),
                detailField(
                    "firstBlockingEffectiveDate",
                    "Effective date of the first previously committed non-opening posting."));
        case OPENING_BALANCE_TOUCHES_NOMINAL_ACCOUNT ->
            List.of(
                detailField(
                    "accountCode",
                    "Nominal accountCode that an opening-balance posting attempted to seed."),
                detailField(
                    "accountType",
                    "Nominal accountType that opening-balance postings are not allowed to touch."));
        case RESULT_HOLDING_ACCOUNT_RESERVED ->
            List.of(
                detailField(
                    "accountCode",
                    "Closing-equity accountCode reserved for generated period-result-transfer postings."));
        case REVERSAL_TARGET_NOT_FOUND ->
            List.of(
                detailField(
                    "priorPostingId",
                    "Previously committed posting that the requested reversal could not find."));
        case REVERSAL_ALREADY_EXISTS ->
            List.of(
                detailField(
                    "priorPostingId",
                    "Previously committed posting that already has a full reversal."));
        case REVERSAL_DOES_NOT_NEGATE_TARGET ->
            List.of(
                detailField(
                    "priorPostingId",
                    "Previously committed posting that the candidate reversal failed to negate."));
      };
    }

    private List<ContractResponse.RejectionDescriptor> detailRejections() {
      return switch (this) {
        case ACCOUNT_STATE_VIOLATIONS -> AccountStateDetailDescriptor.descriptors();
        case BOOK_NOT_INITIALIZED,
            ENTRY_SEMANTICS_VIOLATIONS,
            DUPLICATE_IDEMPOTENCY_KEY,
            BOOK_FUNCTIONAL_CURRENCY_MISMATCH,
            CLOSED_PERIOD_VIOLATION,
            OPENING_BALANCE_WINDOW_CLOSED,
            OPENING_BALANCE_TOUCHES_NOMINAL_ACCOUNT,
            RESULT_HOLDING_ACCOUNT_RESERVED,
            REVERSAL_TARGET_NOT_FOUND,
            REVERSAL_ALREADY_EXISTS,
            REVERSAL_DOES_NOT_NEGATE_TARGET ->
            List.of();
      };
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(
          code(), description(), detailFields(), detailRejections());
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return List.of(
              BOOK_NOT_INITIALIZED,
              ENTRY_SEMANTICS_VIOLATIONS,
              ACCOUNT_STATE_VIOLATIONS,
              DUPLICATE_IDEMPOTENCY_KEY,
              BOOK_FUNCTIONAL_CURRENCY_MISMATCH,
              CLOSED_PERIOD_VIOLATION,
              OPENING_BALANCE_WINDOW_CLOSED,
              OPENING_BALANCE_TOUCHES_NOMINAL_ACCOUNT,
              RESULT_HOLDING_ACCOUNT_RESERVED,
              REVERSAL_TARGET_NOT_FOUND,
              REVERSAL_ALREADY_EXISTS,
              REVERSAL_DOES_NOT_NEGATE_TARGET)
          .stream()
          .map(Descriptor::descriptor)
          .toList();
    }
  }

  /** Canonical metadata for nested account-state detail rejections. */
  enum AccountStateDetailDescriptor {
    UNKNOWN_ACCOUNT,
    INACTIVE_ACCOUNT,
    NON_POSTABLE_ACCOUNT;

    private String code() {
      return switch (this) {
        case UNKNOWN_ACCOUNT -> "unknown-account";
        case INACTIVE_ACCOUNT -> "inactive-account";
        case NON_POSTABLE_ACCOUNT -> "non-postable-account";
      };
    }

    private String description() {
      return switch (this) {
        case UNKNOWN_ACCOUNT -> "One journal line references an undeclared account.";
        case INACTIVE_ACCOUNT -> "One journal line references an inactive account.";
        case NON_POSTABLE_ACCOUNT ->
            "One journal line references a header account that cannot accept direct postings.";
      };
    }

    private List<ContractResponse.FieldDescriptor> detailFields() {
      return switch (this) {
        case UNKNOWN_ACCOUNT ->
            List.of(
                detailField(
                    "accountCode",
                    "Undeclared accountCode referenced by one rejected journal line."));
        case INACTIVE_ACCOUNT ->
            List.of(
                detailField(
                    "accountCode",
                    "Inactive accountCode referenced by one rejected journal line."));
        case NON_POSTABLE_ACCOUNT ->
            List.of(
                detailField(
                    "accountCode", "Header accountCode referenced by one rejected journal line."),
                detailField(
                    "accountNodeKind", "Declared accountNodeKind that forbids direct postings."));
      };
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(
          code(), description(), detailFields(), List.of());
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return List.of(UNKNOWN_ACCOUNT, INACTIVE_ACCOUNT, NON_POSTABLE_ACCOUNT).stream()
          .map(AccountStateDetailDescriptor::descriptor)
          .toList();
    }
  }
}
