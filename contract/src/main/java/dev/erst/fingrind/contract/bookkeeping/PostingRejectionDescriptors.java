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
      case PostingRejection.PostingEffectiveDateInFuture _ ->
          Descriptor.POSTING_EFFECTIVE_DATE_IN_FUTURE;
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
      case ReversalTargetIsReversal _ -> Descriptor.REVERSAL_TARGET_IS_REVERSAL;
      case PostingRejection.ReversalAlreadyExists _ -> Descriptor.REVERSAL_ALREADY_EXISTS;
      case PostingRejection.ReversalDoesNotNegateTarget _ ->
          Descriptor.REVERSAL_DOES_NOT_NEGATE_TARGET;
    };
  }

  /** Canonical owner for top-level detail-field sets published by posting rejections. */
  private enum DetailFieldOwner {
    NONE {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of();
      }
    },
    ENTRY_SEMANTICS_VIOLATIONS {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField(
                "violations",
                "Array of ordered entry-semantics issue objects with stable code, field, message, category, and repair."));
      }
    },
    ACCOUNT_STATE_VIOLATIONS {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField(
                "violations",
                "Array of ordered account-state issue objects with stable code, field, message, category, repair, accountCode, and optional accountNodeKind."));
      }
    },
    EFFECTIVE_DATE_HORIZON {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField(
                "attemptedEffectiveDate", "Rejected effective date from the posting request."),
            detailField("currentUtcDate", "Current UTC date resolved from the application clock."));
      }
    },
    FUNCTIONAL_CURRENCY_MISMATCH {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField("functionalCurrency", "Functional currency declared by the selected book."),
            detailField(
                "attemptedCurrency", "Rejected journal-entry currency from the posting request."));
      }
    },
    CLOSED_PERIOD_VIOLATION {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField(
                "transferredThroughEffectiveDate",
                "Inclusive effective date through which postings are already closed."),
            detailField(
                "attemptedEffectiveDate", "Rejected effective date from the posting request."));
      }
    },
    OPENING_POSITION_WINDOW_CLOSED {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField(
                "firstBlockingPostingKind",
                "Previously committed posting kind that closed the one-time OPENING_POSITION admission window."),
            detailField(
                "firstBlockingEffectiveDate",
                "Effective date of the first previously committed posting after the opening-position window closed."));
      }
    },
    OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField(
                "accountCode",
                "Nominal accountCode that an OPENING_POSITION request attempted to seed."),
            detailField(
                "accountType",
                "Nominal accountType that OPENING_POSITION requests are not allowed to touch."));
      }
    },
    RESERVED_RESULT_CLASSIFICATION {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField(
                "accountCode", "Declared accountCode that uses the reserved close classification."),
            detailField(
                "financialPositionLineClassification",
                "Reserved financialPositionLineClassification that caller-authored postings may not touch directly."));
      }
    },
    REVERSAL_TARGET_NOT_FOUND {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField(
                "priorPostingId",
                "Previously committed posting that the requested reversal could not find."));
      }
    },
    REVERSAL_TARGET_IS_REVERSAL {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField(
                "priorPostingId",
                "Previously committed reversal posting that the requested reversal attempted to target."));
      }
    },
    REVERSAL_ALREADY_EXISTS {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField(
                "priorPostingId",
                "Previously committed posting that already has a full reversal."));
      }
    },
    REVERSAL_DOES_NOT_NEGATE_TARGET {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            detailField(
                "priorPostingId",
                "Previously committed posting that the candidate reversal failed to negate."));
      }
    };

    abstract List<ContractResponse.FieldDescriptor> descriptors();
  }

  /** Canonical owner for nested rejection catalogs published by posting rejections. */
  private enum DetailRejectionOwner {
    NONE {
      @Override
      List<ContractResponse.RejectionDescriptor> descriptors() {
        return List.of();
      }
    },
    ENTRY_SEMANTICS {
      @Override
      List<ContractResponse.RejectionDescriptor> descriptors() {
        return EntrySemanticsViolationOwner.descriptors();
      }
    },
    ACCOUNT_STATE {
      @Override
      List<ContractResponse.RejectionDescriptor> descriptors() {
        return AccountStateViolationOwner.descriptors();
      }
    };

    abstract List<ContractResponse.RejectionDescriptor> descriptors();
  }

  /** Canonical posting rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_NOT_INITIALIZED(
        "posting-book-not-initialized",
        "Posting refused because the selected book does not exist or has not been initialized with "
            + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
            + ".",
        DetailFieldOwner.NONE,
        DetailRejectionOwner.NONE),
    ENTRY_SEMANTICS_VIOLATIONS(
        "entry-semantics-violations",
        "Posting refused because one or more canonical entry-semantics violations were detected. details.violations[] carries ordered issue objects with stable code, field, message, category, and repair metadata.",
        DetailFieldOwner.ENTRY_SEMANTICS_VIOLATIONS,
        DetailRejectionOwner.ENTRY_SEMANTICS),
    ACCOUNT_STATE_VIOLATIONS(
        "account-state-violations",
        "Posting refused because one or more posting attributes reference undeclared, inactive, or non-postable accounts, would append inventory before the account horizon, would drive inventory quantity below zero, or would reduce inventory carrying cost below zero.",
        DetailFieldOwner.ACCOUNT_STATE_VIOLATIONS,
        DetailRejectionOwner.ACCOUNT_STATE),
    IDEMPOTENCY_KEY_CONFLICT(
        "idempotency-key-conflict",
        "Posting refused because the selected book already contains this idempotency key for a different normalized request.",
        DetailFieldOwner.NONE,
        DetailRejectionOwner.NONE),
    POSTING_EFFECTIVE_DATE_IN_FUTURE(
        "posting-effective-date-in-future",
        "Posting refused because its effective date falls after the current UTC date.",
        DetailFieldOwner.EFFECTIVE_DATE_HORIZON,
        DetailRejectionOwner.NONE),
    BOOK_FUNCTIONAL_CURRENCY_MISMATCH(
        "book-functional-currency-mismatch",
        "Posting refused because one or more journal-line currencies do not match the selected book functional currency.",
        DetailFieldOwner.FUNCTIONAL_CURRENCY_MISMATCH,
        DetailRejectionOwner.NONE),
    CLOSED_PERIOD_VIOLATION(
        "closed-period-violation",
        "Posting refused because its effective date falls inside one transferred reporting period.",
        DetailFieldOwner.CLOSED_PERIOD_VIOLATION,
        DetailRejectionOwner.NONE),
    OPENING_POSITION_WINDOW_CLOSED(
        "opening-position-window-closed",
        "Posting refused because OPENING_POSITION entries are allowed only before the first committed posting in the selected book.",
        DetailFieldOwner.OPENING_POSITION_WINDOW_CLOSED,
        DetailRejectionOwner.NONE),
    OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT(
        "opening-position-touches-nominal-account",
        "Posting refused because OPENING_POSITION entries may seed only asset, liability, or equity accounts.",
        DetailFieldOwner.OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT,
        DetailRejectionOwner.NONE),
    RESERVED_RESULT_CLASSIFICATION(
        "reserved-result-classification",
        "Posting refused because the selected account uses one close-reserved financialPositionLineClassification.",
        DetailFieldOwner.RESERVED_RESULT_CLASSIFICATION,
        DetailRejectionOwner.NONE),
    REVERSAL_TARGET_NOT_FOUND(
        "reversal-target-not-found",
        "Posting refused because reversal.priorPostingId does not identify a committed posting in this book.",
        DetailFieldOwner.REVERSAL_TARGET_NOT_FOUND,
        DetailRejectionOwner.NONE),
    REVERSAL_TARGET_IS_REVERSAL(
        "reversal-target-is-reversal",
        "Posting refused because reversal.priorPostingId identifies one posting that is already a reversal.",
        DetailFieldOwner.REVERSAL_TARGET_IS_REVERSAL,
        DetailRejectionOwner.NONE),
    REVERSAL_ALREADY_EXISTS(
        "reversal-already-exists",
        "Posting refused because the selected prior posting already has a full reversal.",
        DetailFieldOwner.REVERSAL_ALREADY_EXISTS,
        DetailRejectionOwner.NONE),
    REVERSAL_DOES_NOT_NEGATE_TARGET(
        "reversal-does-not-negate-target",
        "Posting refused because the candidate reversal does not exactly negate the target posting.",
        DetailFieldOwner.REVERSAL_DOES_NOT_NEGATE_TARGET,
        DetailRejectionOwner.NONE);

    private final String code;
    private final String description;
    private final DetailFieldOwner detailFields;
    private final DetailRejectionOwner detailRejections;

    Descriptor(
        String code,
        String description,
        DetailFieldOwner detailFields,
        DetailRejectionOwner detailRejections) {
      this.code = Objects.requireNonNull(code, "code");
      this.description = Objects.requireNonNull(description, "description");
      this.detailFields = Objects.requireNonNull(detailFields, "detailFields");
      this.detailRejections = Objects.requireNonNull(detailRejections, "detailRejections");
    }

    private String code() {
      return code;
    }

    private String description() {
      return description;
    }

    private List<ContractResponse.FieldDescriptor> detailFields() {
      return detailFields.descriptors();
    }

    private List<ContractResponse.RejectionDescriptor> detailRejections() {
      return detailRejections.descriptors();
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(
          code(), description(), detailFields(), detailRejections());
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return List.of(values()).stream().map(Descriptor::descriptor).toList();
    }
  }
}
