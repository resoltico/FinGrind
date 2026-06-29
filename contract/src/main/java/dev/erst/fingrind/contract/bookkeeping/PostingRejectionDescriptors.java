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
    return AccountStateViolationOwner.code(Objects.requireNonNull(violation, "violation"));
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
      case PostingRejection.IdempotencyKeyConflict _ -> Descriptor.IDEMPOTENCY_KEY_CONFLICT;
      case PostingRejection.BookFunctionalCurrencyMismatch _ ->
          Descriptor.BOOK_FUNCTIONAL_CURRENCY_MISMATCH;
      case PostingRejection.SweptInterimResultViolation _ -> Descriptor.CLOSED_PERIOD_VIOLATION;
      case PostingRejection.OpeningPositionWindowClosed _ ->
          Descriptor.OPENING_POSITION_WINDOW_CLOSED;
      case PostingRejection.OpeningPositionTouchesNominalAccount _ ->
          Descriptor.OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT;
      case PostingRejection.ReservedResultClassification _ ->
          Descriptor.RESERVED_RESULT_CLASSIFICATION;
      case PostingRejection.ReversalTargetNotFound _ -> Descriptor.REVERSAL_TARGET_NOT_FOUND;
      case PostingRejection.ReversalAlreadyExists _ -> Descriptor.REVERSAL_ALREADY_EXISTS;
      case PostingRejection.ReversalDoesNotNegateTarget _ ->
          Descriptor.REVERSAL_DOES_NOT_NEGATE_TARGET;
    };
  }

  /** Canonical posting rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_NOT_INITIALIZED,
    ACCOUNT_STATE_VIOLATIONS,
    ENTRY_SEMANTICS_VIOLATIONS,
    IDEMPOTENCY_KEY_CONFLICT,
    BOOK_FUNCTIONAL_CURRENCY_MISMATCH,
    CLOSED_PERIOD_VIOLATION,
    OPENING_POSITION_WINDOW_CLOSED,
    OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT,
    RESERVED_RESULT_CLASSIFICATION,
    REVERSAL_TARGET_NOT_FOUND,
    REVERSAL_ALREADY_EXISTS,
    REVERSAL_DOES_NOT_NEGATE_TARGET;

    private String code() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED -> "posting-book-not-initialized";
        case ACCOUNT_STATE_VIOLATIONS -> "account-state-violations";
        case ENTRY_SEMANTICS_VIOLATIONS -> "entry-semantics-violations";
        case IDEMPOTENCY_KEY_CONFLICT -> "idempotency-key-conflict";
        case BOOK_FUNCTIONAL_CURRENCY_MISMATCH -> "book-functional-currency-mismatch";
        case CLOSED_PERIOD_VIOLATION -> "closed-period-violation";
        case OPENING_POSITION_WINDOW_CLOSED -> "opening-position-window-closed";
        case OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT -> "opening-position-touches-nominal-account";
        case RESERVED_RESULT_CLASSIFICATION -> "reserved-result-classification";
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
            "Posting refused because one or more journal lines reference undeclared, inactive, or non-postable accounts.";
        case ENTRY_SEMANTICS_VIOLATIONS ->
            "Posting refused because one or more canonical entry-semantics violations were detected. details.violations[] carries ordered issue objects with stable code, field, message, category, and repair metadata.";
        case IDEMPOTENCY_KEY_CONFLICT ->
            "Posting refused because the selected book already contains this idempotency key for a different normalized request.";
        case BOOK_FUNCTIONAL_CURRENCY_MISMATCH ->
            "Posting refused because one or more journal-line currencies do not match the selected book functional currency.";
        case CLOSED_PERIOD_VIOLATION ->
            "Posting refused because its effective date falls inside one transferred reporting period.";
        case OPENING_POSITION_WINDOW_CLOSED ->
            "Posting refused because OPENING_POSITION entries are allowed only before the first committed posting in the selected book.";
        case OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT ->
            "Posting refused because OPENING_POSITION entries may seed only asset, liability, or equity accounts.";
        case RESERVED_RESULT_CLASSIFICATION ->
            "Posting refused because the selected account uses one close-reserved financialPositionLineClassification.";
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
        case BOOK_NOT_INITIALIZED, IDEMPOTENCY_KEY_CONFLICT -> List.of();
        case ACCOUNT_STATE_VIOLATIONS ->
            List.of(
                detailField(
                    "violations",
                    "Array of ordered account-state issue objects with stable code, field, message, category, repair, accountCode, and optional accountNodeKind."));
        case ENTRY_SEMANTICS_VIOLATIONS ->
            List.of(
                detailField(
                    "violations",
                    "Array of ordered entry-semantics issue objects with stable code, field, message, category, and repair."));
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
        case OPENING_POSITION_WINDOW_CLOSED ->
            List.of(
                detailField(
                    "firstBlockingPostingKind",
                    "Previously committed posting kind that closed the one-time OPENING_POSITION admission window."),
                detailField(
                    "firstBlockingEffectiveDate",
                    "Effective date of the first previously committed posting after the opening-position window closed."));
        case OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT ->
            List.of(
                detailField(
                    "accountCode",
                    "Nominal accountCode that an OPENING_POSITION request attempted to seed."),
                detailField(
                    "accountType",
                    "Nominal accountType that OPENING_POSITION requests are not allowed to touch."));
        case RESERVED_RESULT_CLASSIFICATION ->
            List.of(
                detailField(
                    "accountCode",
                    "Declared accountCode that uses the reserved close classification."),
                detailField(
                    "financialPositionLineClassification",
                    "Reserved financialPositionLineClassification that caller-authored postings may not touch directly."));
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
        case ACCOUNT_STATE_VIOLATIONS -> AccountStateViolationOwner.descriptors();
        case ENTRY_SEMANTICS_VIOLATIONS -> EntrySemanticsViolationOwner.descriptors();
        case BOOK_NOT_INITIALIZED,
            IDEMPOTENCY_KEY_CONFLICT,
            BOOK_FUNCTIONAL_CURRENCY_MISMATCH,
            CLOSED_PERIOD_VIOLATION,
            OPENING_POSITION_WINDOW_CLOSED,
            OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT,
            RESERVED_RESULT_CLASSIFICATION,
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
              IDEMPOTENCY_KEY_CONFLICT,
              BOOK_FUNCTIONAL_CURRENCY_MISMATCH,
              CLOSED_PERIOD_VIOLATION,
              OPENING_POSITION_WINDOW_CLOSED,
              OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT,
              RESERVED_RESULT_CLASSIFICATION,
              REVERSAL_TARGET_NOT_FOUND,
              REVERSAL_ALREADY_EXISTS,
              REVERSAL_DOES_NOT_NEGATE_TARGET)
          .stream()
          .map(Descriptor::descriptor)
          .toList();
    }
  }
}
