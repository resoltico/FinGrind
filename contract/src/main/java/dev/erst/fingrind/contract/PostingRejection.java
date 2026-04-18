package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Closed family of domain rejections that can refuse a posting request deterministically. */
public sealed interface PostingRejection
    permits PostingRejection.BookNotInitialized,
        PostingRejection.AccountStateViolations,
        PostingRejection.DuplicateIdempotencyKey,
        PostingRejection.ReversalTargetNotFound,
        PostingRejection.ReversalAlreadyExists,
        PostingRejection.ReversalDoesNotNegateTarget {

  /** Returns the stable wire code for one posting rejection instance. */
  static String wireCode(PostingRejection rejection) {
    return descriptorFor(rejection).code();
  }

  /** Returns the stable wire code for the missing-book posting rejection. */
  static String bookNotInitializedCode() {
    return Descriptor.BOOK_NOT_INITIALIZED.code();
  }

  /** Returns the stable wire code for one account-state violation detail. */
  static String wireCode(AccountStateViolation violation) {
    return detailDescriptorFor(violation).code();
  }

  /** Returns the canonical machine descriptors for every permitted posting rejection subtype. */
  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  /** Rejection for a posting request against a missing or uninitialized book. */
  record BookNotInitialized() implements PostingRejection {}

  /** Closed family of account-state issues surfaced while validating one posting request. */
  sealed interface AccountStateViolation
      permits PostingRejection.UnknownAccount, PostingRejection.InactiveAccount {}

  /** Rejection for a posting request with one or more account-state violations. */
  record AccountStateViolations(List<AccountStateViolation> violations)
      implements PostingRejection {
    /** Validates the account-state violation payload. */
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

  /** Duplicate idempotency rejection for a book-local request identity that already exists. */
  record DuplicateIdempotencyKey() implements PostingRejection {}

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

  private static AccountStateDetailDescriptor detailDescriptorFor(AccountStateViolation violation) {
    return switch (Objects.requireNonNull(violation, "violation")) {
      case PostingRejection.UnknownAccount _ -> AccountStateDetailDescriptor.UNKNOWN_ACCOUNT;
      case PostingRejection.InactiveAccount _ -> AccountStateDetailDescriptor.INACTIVE_ACCOUNT;
    };
  }

  /** Canonical posting rejection metadata keyed by stable wire code. */
  @SuppressWarnings("ImmutableEnumChecker")
  enum Descriptor {
    BOOK_NOT_INITIALIZED(
        "posting-book-not-initialized",
        "Posting refused because the selected book does not exist or has not been initialized with "
            + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
            + "."),
    ACCOUNT_STATE_VIOLATIONS(
        "account-state-violations",
        "Posting refused because one or more journal lines reference undeclared or inactive accounts.",
        List.of(
            detailField(
                "violations",
                "Array of per-line account-state issue objects with stable code and accountCode.")),
        AccountStateDetailDescriptor.descriptors()),
    DUPLICATE_IDEMPOTENCY_KEY(
        "duplicate-idempotency-key",
        "Posting refused because the selected book already contains the same idempotency key."),
    REVERSAL_TARGET_NOT_FOUND(
        "reversal-target-not-found",
        "Posting refused because reversal.priorPostingId does not identify a committed posting in this book.",
        List.of(
            detailField(
                "priorPostingId",
                "Previously committed posting that the requested reversal could not find."))),
    REVERSAL_ALREADY_EXISTS(
        "reversal-already-exists",
        "Posting refused because the selected prior posting already has a full reversal.",
        List.of(
            detailField(
                "priorPostingId",
                "Previously committed posting that already has a full reversal."))),
    REVERSAL_DOES_NOT_NEGATE_TARGET(
        "reversal-does-not-negate-target",
        "Posting refused because the candidate reversal does not exactly negate the target posting.",
        List.of(
            detailField(
                "priorPostingId",
                "Previously committed posting that the candidate reversal failed to negate.")));

    private final String code;
    private final String description;
    private final List<ContractResponse.FieldDescriptor> detailFields;
    private final List<ContractResponse.RejectionDescriptor> detailRejections;

    Descriptor(String code, String description) {
      this(code, description, List.of(), List.of());
    }

    Descriptor(
        String code, String description, List<ContractResponse.FieldDescriptor> detailFields) {
      this(code, description, detailFields, List.of());
    }

    Descriptor(
        String code,
        String description,
        List<ContractResponse.FieldDescriptor> detailFields,
        List<ContractResponse.RejectionDescriptor> detailRejections) {
      this.code = Objects.requireNonNull(code, "code");
      this.description = Objects.requireNonNull(description, "description");
      this.detailFields = List.copyOf(Objects.requireNonNull(detailFields, "detailFields"));
      this.detailRejections =
          List.copyOf(Objects.requireNonNull(detailRejections, "detailRejections"));
    }

    private String code() {
      return code;
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(
          code, description, detailFields, detailRejections);
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return Arrays.stream(values()).map(Descriptor::descriptor).toList();
    }
  }

  /** Canonical metadata for nested account-state detail rejections. */
  @SuppressWarnings("ImmutableEnumChecker")
  enum AccountStateDetailDescriptor {
    UNKNOWN_ACCOUNT(
        "unknown-account",
        "One journal line references an undeclared account.",
        List.of(
            detailField(
                "accountCode", "Undeclared accountCode referenced by one rejected journal line."))),
    INACTIVE_ACCOUNT(
        "inactive-account",
        "One journal line references an inactive account.",
        List.of(
            detailField(
                "accountCode", "Inactive accountCode referenced by one rejected journal line.")));

    private final String code;
    private final String description;
    private final List<ContractResponse.FieldDescriptor> detailFields;

    AccountStateDetailDescriptor(
        String code, String description, List<ContractResponse.FieldDescriptor> detailFields) {
      this.code = Objects.requireNonNull(code, "code");
      this.description = Objects.requireNonNull(description, "description");
      this.detailFields = List.copyOf(Objects.requireNonNull(detailFields, "detailFields"));
    }

    private String code() {
      return code;
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(code, description, detailFields, List.of());
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return Arrays.stream(values()).map(AccountStateDetailDescriptor::descriptor).toList();
    }
  }
}
