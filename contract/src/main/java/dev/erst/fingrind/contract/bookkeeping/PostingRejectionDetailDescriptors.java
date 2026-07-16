package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;

/** Owns detail payload catalogs published beside canonical posting rejections. */
final class PostingRejectionDetailDescriptors {
  private PostingRejectionDetailDescriptors() {}

  private static ContractResponse.FieldDescriptor field(String name, String description) {
    return new ContractResponse.FieldDescriptor(name, description);
  }

  static List<ContractResponse.FieldDescriptor> fields(FieldOwner owner) {
    return owner.descriptors();
  }

  /** Canonical owner for top-level detail-field sets published by posting rejections. */
  enum FieldOwner {
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
            field(
                "violations",
                "Array of ordered entry-semantics issue objects with stable code, field, message, category, and repair."));
      }
    },
    ACCOUNT_STATE_VIOLATIONS {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            field(
                "violations",
                "Array of ordered account-state issue objects with stable code, field, message, category, repair, accountCode, and optional accountNodeKind."));
      }
    },
    EFFECTIVE_DATE_HORIZON {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            field("attemptedEffectiveDate", "Rejected effective date from the posting request."),
            field("currentUtcDate", "Current UTC date resolved from the application clock."));
      }
    },
    FUNCTIONAL_CURRENCY_MISMATCH {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            field("functionalCurrency", "Functional currency declared by the selected book."),
            field(
                "attemptedCurrency", "Rejected journal-entry currency from the posting request."));
      }
    },
    CLOSED_PERIOD_VIOLATION {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            field(
                "transferredThroughEffectiveDate",
                "Inclusive effective date through which postings are already closed."),
            field("attemptedEffectiveDate", "Rejected effective date from the posting request."));
      }
    },
    OPENING_POSITION_WINDOW_CLOSED {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            field(
                "firstBlockingPostingKind",
                "Previously committed posting kind that closed the one-time OPENING_POSITION admission window."),
            field(
                "firstBlockingEffectiveDate",
                "Effective date of the first previously committed posting after the opening-position window closed."));
      }
    },
    OPENING_POSITION_TOUCHES_NOMINAL_ACCOUNT {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            field(
                "accountCode",
                "Nominal accountCode that an OPENING_POSITION request attempted to seed."),
            field(
                "accountType",
                "Nominal accountType that OPENING_POSITION requests are not allowed to touch."));
      }
    },
    RESERVED_RESULT_CLASSIFICATION {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            field(
                "accountCode", "Declared accountCode that uses the reserved close classification."),
            field(
                "financialPositionLineClassification",
                "Reserved financialPositionLineClassification that caller-authored postings may not touch directly."));
      }
    },
    REVERSAL_TARGET_NOT_FOUND {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            field(
                "priorPostingId",
                "Previously committed posting that the requested reversal could not find."));
      }
    },
    REVERSAL_TARGET_IS_REVERSAL {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            field(
                "priorPostingId",
                "Previously committed reversal posting that the requested reversal attempted to target."));
      }
    },
    REVERSAL_ALREADY_EXISTS {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            field(
                "priorPostingId",
                "Previously committed posting that already has a full reversal."));
      }
    },
    REVERSAL_DOES_NOT_NEGATE_TARGET {
      @Override
      List<ContractResponse.FieldDescriptor> descriptors() {
        return List.of(
            field(
                "priorPostingId",
                "Previously committed posting that the candidate reversal failed to negate."));
      }
    };

    abstract List<ContractResponse.FieldDescriptor> descriptors();
  }

  /** Canonical owner for nested rejection catalogs published by posting rejections. */
  enum RejectionOwner {
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
}
