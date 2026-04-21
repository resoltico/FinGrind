package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
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
      case PostingRejection.DuplicateIdempotencyKey _ -> Descriptor.DUPLICATE_IDEMPOTENCY_KEY;
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
    };
  }

  /** Canonical posting rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_NOT_INITIALIZED,
    ACCOUNT_STATE_VIOLATIONS,
    DUPLICATE_IDEMPOTENCY_KEY,
    REVERSAL_TARGET_NOT_FOUND,
    REVERSAL_ALREADY_EXISTS,
    REVERSAL_DOES_NOT_NEGATE_TARGET;

    private String code() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED -> "posting-book-not-initialized";
        case ACCOUNT_STATE_VIOLATIONS -> "account-state-violations";
        case DUPLICATE_IDEMPOTENCY_KEY -> "duplicate-idempotency-key";
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
        case DUPLICATE_IDEMPOTENCY_KEY ->
            "Posting refused because the selected book already contains the same idempotency key.";
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
            DUPLICATE_IDEMPOTENCY_KEY,
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
              ACCOUNT_STATE_VIOLATIONS,
              DUPLICATE_IDEMPOTENCY_KEY,
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
    INACTIVE_ACCOUNT;

    private String code() {
      return switch (this) {
        case UNKNOWN_ACCOUNT -> "unknown-account";
        case INACTIVE_ACCOUNT -> "inactive-account";
      };
    }

    private String description() {
      return switch (this) {
        case UNKNOWN_ACCOUNT -> "One journal line references an undeclared account.";
        case INACTIVE_ACCOUNT -> "One journal line references an inactive account.";
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
      };
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(
          code(), description(), detailFields(), List.of());
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return List.of(UNKNOWN_ACCOUNT, INACTIVE_ACCOUNT).stream()
          .map(AccountStateDetailDescriptor::descriptor)
          .toList();
    }
  }
}
