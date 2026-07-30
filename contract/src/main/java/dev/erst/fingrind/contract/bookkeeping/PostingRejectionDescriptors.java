package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.FieldDescriptor;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
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

  static List<RejectionDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  private static Descriptor descriptorFor(PostingRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case FoundationalPostingRejection foundationalRejection ->
          foundationalDescriptor(foundationalRejection);
      case WorkflowPostingRejection workflowRejection -> workflowDescriptor(workflowRejection);
    };
  }

  private static Descriptor foundationalDescriptor(FoundationalPostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ -> Descriptor.BOOK_NOT_INITIALIZED;
      case PostingRejection.AccountStateViolations _ -> Descriptor.ACCOUNT_STATE_VIOLATIONS;
      case PostingRejection.EntrySemanticsViolations _ -> Descriptor.ENTRY_SEMANTICS_VIOLATIONS;
      case PostingRejection.IdempotencyKeyConflict _ -> Descriptor.IDEMPOTENCY_KEY_CONFLICT;
      case PostingEffectiveDateBeforeBookStart _ ->
          Descriptor.POSTING_EFFECTIVE_DATE_BEFORE_BOOK_START;
      case PostingRejection.PostingEffectiveDateInFuture _ ->
          Descriptor.POSTING_EFFECTIVE_DATE_IN_FUTURE;
      case PostingRejection.BookFunctionalCurrencyMismatch _ ->
          Descriptor.BOOK_FUNCTIONAL_CURRENCY_MISMATCH;
      case PostingRejection.SweptInterimResultViolation _ -> Descriptor.CLOSED_PERIOD_VIOLATION;
    };
  }

  private static Descriptor workflowDescriptor(WorkflowPostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.OpeningPositionWindowClosed _ ->
          Descriptor.OPENING_POSITION_WINDOW_CLOSED;
      case PostingRejection.OpeningPositionTouchesNominalAccount _ ->
          Descriptor.OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT;
      case PostingRejection.ReservedResultClassification _ ->
          Descriptor.RESERVED_RESULT_CLASSIFICATION;
      case PostingRejection.ReversalTargetNotFound _ -> Descriptor.REVERSAL_TARGET_NOT_FOUND;
      case ReversalTargetIsReversal _ -> Descriptor.REVERSAL_TARGET_IS_REVERSAL;
      case PostingRejection.ReversalAlreadyExists _ -> Descriptor.REVERSAL_ALREADY_EXISTS;
      case PostingRejection.ReversalDoesNotNegateTarget _ ->
          Descriptor.REVERSAL_DOES_NOT_NEGATE_TARGET;
    };
  }

  /** Canonical posting rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_NOT_INITIALIZED(
        "posting-book-not-initialized",
        "Posting refused because the selected book does not exist or has not been initialized with "
            + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
            + ".",
        FailureCategory.PRECONDITION,
        PostingRejectionDetailDescriptors.FieldOwner.NONE,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    ENTRY_SEMANTICS_VIOLATIONS(
        "entry-semantics-violations",
        "Posting refused because one or more canonical entry-semantics violations were detected. details.violations[] carries ordered issue objects with stable code, field, message, category, and repair metadata.",
        PostingRejectionDetailDescriptors.FieldOwner.ENTRY_SEMANTICS_VIOLATIONS,
        PostingRejectionDetailDescriptors.RejectionOwner.ENTRY_SEMANTICS),
    ACCOUNT_STATE_VIOLATIONS(
        "account-state-violations",
        "Posting refused because one or more posting attributes reference undeclared, inactive, or non-postable accounts, would append inventory before the account horizon, would drive inventory quantity below zero, or would reduce inventory carrying cost below zero.",
        PostingRejectionDetailDescriptors.FieldOwner.ACCOUNT_STATE_VIOLATIONS,
        PostingRejectionDetailDescriptors.RejectionOwner.ACCOUNT_STATE),
    IDEMPOTENCY_KEY_CONFLICT(
        "idempotency-key-conflict",
        "Posting refused because the selected book already contains this idempotency key for a different normalized request.",
        PostingRejectionDetailDescriptors.FieldOwner.NONE,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    POSTING_EFFECTIVE_DATE_BEFORE_BOOK_START(
        "posting-effective-date-before-book-start",
        "Posting refused because its effective date predates the immutable book-start effective date.",
        PostingRejectionDetailDescriptors.FieldOwner.BOOK_START_EFFECTIVE_DATE,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    POSTING_EFFECTIVE_DATE_IN_FUTURE(
        "posting-effective-date-in-future",
        "Posting refused because its effective date falls after the current UTC date.",
        PostingRejectionDetailDescriptors.FieldOwner.EFFECTIVE_DATE_HORIZON,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    BOOK_FUNCTIONAL_CURRENCY_MISMATCH(
        "book-functional-currency-mismatch",
        "Posting refused because one or more journal-line currencies do not match the selected book functional currency.",
        PostingRejectionDetailDescriptors.FieldOwner.FUNCTIONAL_CURRENCY_MISMATCH,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    CLOSED_PERIOD_VIOLATION(
        "closed-period-violation",
        "Posting refused because its effective date falls inside one transferred reporting period.",
        PostingRejectionDetailDescriptors.FieldOwner.CLOSED_PERIOD_VIOLATION,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    OPENING_POSITION_WINDOW_CLOSED(
        "opening-position-window-closed",
        "Posting refused because OPENING_POSITION entries are allowed only before the first committed posting in the selected book.",
        PostingRejectionDetailDescriptors.FieldOwner.OPENING_POSITION_WINDOW_CLOSED,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT(
        "opening-position-touches-nominal-account",
        "Posting refused because OPENING_POSITION entries may seed only asset, liability, or equity accounts.",
        PostingRejectionDetailDescriptors.FieldOwner.OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    RESERVED_RESULT_CLASSIFICATION(
        "reserved-result-classification",
        "Posting refused because the selected account uses one close-reserved financialPositionLineClassification.",
        PostingRejectionDetailDescriptors.FieldOwner.RESERVED_RESULT_CLASSIFICATION,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    REVERSAL_TARGET_NOT_FOUND(
        "reversal-target-not-found",
        "Posting refused because reversal.priorPostingId does not identify a committed posting in this book.",
        PostingRejectionDetailDescriptors.FieldOwner.REVERSAL_TARGET_NOT_FOUND,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    REVERSAL_TARGET_IS_REVERSAL(
        "reversal-target-is-reversal",
        "Posting refused because reversal.priorPostingId identifies one posting that is already a reversal.",
        PostingRejectionDetailDescriptors.FieldOwner.REVERSAL_TARGET_IS_REVERSAL,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    REVERSAL_ALREADY_EXISTS(
        "reversal-already-exists",
        "Posting refused because the selected prior posting already has a full reversal.",
        PostingRejectionDetailDescriptors.FieldOwner.REVERSAL_ALREADY_EXISTS,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE),
    REVERSAL_DOES_NOT_NEGATE_TARGET(
        "reversal-does-not-negate-target",
        "Posting refused because the candidate reversal does not exactly negate the target posting.",
        PostingRejectionDetailDescriptors.FieldOwner.REVERSAL_DOES_NOT_NEGATE_TARGET,
        PostingRejectionDetailDescriptors.RejectionOwner.NONE);

    private final String code;
    private final String description;
    private final FailureCategory category;
    private final PostingRejectionDetailDescriptors.FieldOwner detailFields;
    private final PostingRejectionDetailDescriptors.RejectionOwner detailRejections;

    Descriptor(
        String code,
        String description,
        PostingRejectionDetailDescriptors.FieldOwner detailFields,
        PostingRejectionDetailDescriptors.RejectionOwner detailRejections) {
      this(code, description, FailureCategory.DOMAIN_SEMANTIC, detailFields, detailRejections);
    }

    Descriptor(
        String code,
        String description,
        FailureCategory category,
        PostingRejectionDetailDescriptors.FieldOwner detailFields,
        PostingRejectionDetailDescriptors.RejectionOwner detailRejections) {
      this.code = Objects.requireNonNull(code, "code");
      this.description = Objects.requireNonNull(description, "description");
      this.category = Objects.requireNonNull(category, "category");
      this.detailFields = Objects.requireNonNull(detailFields, "detailFields");
      this.detailRejections = Objects.requireNonNull(detailRejections, "detailRejections");
    }

    private String code() {
      return code;
    }

    private String description() {
      return description;
    }

    private List<FieldDescriptor> detailFields() {
      return PostingRejectionDetailDescriptors.fields(detailFields);
    }

    private List<RejectionDescriptor> detailRejections() {
      return detailRejections.descriptors();
    }

    private RejectionDescriptor descriptor() {
      return new RejectionDescriptor(
          code(), category(), 2, description(), detailFields(), detailRejections());
    }

    private FailureCategory category() {
      return category;
    }

    private static List<RejectionDescriptor> descriptors() {
      return List.of(values()).stream().map(Descriptor::descriptor).toList();
    }
  }
}
