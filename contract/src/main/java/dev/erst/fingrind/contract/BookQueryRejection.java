package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Closed family of deterministic rejections for query-side commands. */
public sealed interface BookQueryRejection
    permits BookQueryRejection.BookNotInitialized,
        BookQueryRejection.UnknownAccount,
        BookQueryRejection.PostingNotFound {

  /** Returns the stable wire code for one query rejection instance. */
  static String wireCode(BookQueryRejection rejection) {
    return descriptorFor(rejection).code();
  }

  /** Returns the stable wire code for the missing-book query rejection. */
  static String bookNotInitializedCode() {
    return Descriptor.BOOK_NOT_INITIALIZED.code();
  }

  /** Returns the canonical machine descriptors for every permitted query rejection. */
  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  /** Rejection for a query against a missing or uninitialized book. */
  record BookNotInitialized() implements BookQueryRejection {}

  /** Rejection for a query that names an undeclared account. */
  record UnknownAccount(AccountCode accountCode) implements BookQueryRejection {
    /** Validates the missing account descriptor. */
    public UnknownAccount {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Rejection for a query that names a posting that does not exist in the selected book. */
  record PostingNotFound(PostingId postingId) implements BookQueryRejection {
    /** Validates the missing posting descriptor. */
    public PostingNotFound {
      Objects.requireNonNull(postingId, "postingId");
    }
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(name, description);
  }

  private static Descriptor descriptorFor(BookQueryRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookQueryRejection.BookNotInitialized _ -> Descriptor.BOOK_NOT_INITIALIZED;
      case BookQueryRejection.UnknownAccount _ -> Descriptor.UNKNOWN_ACCOUNT;
      case BookQueryRejection.PostingNotFound _ -> Descriptor.POSTING_NOT_FOUND;
    };
  }

  /** Canonical query rejection metadata keyed by stable wire code. */
  @SuppressWarnings("ImmutableEnumChecker")
  enum Descriptor {
    BOOK_NOT_INITIALIZED(
        "query-book-not-initialized",
        "Query refused because the selected book does not exist or has not been initialized with "
            + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
            + "."),
    UNKNOWN_ACCOUNT(
        "unknown-account",
        "Query refused because the selected accountCode is not declared in this book.",
        List.of(
            detailField(
                "accountCode",
                "Undeclared accountCode supplied by the caller for the rejected query."))),
    POSTING_NOT_FOUND(
        "posting-not-found",
        "Query refused because the selected postingId does not identify a committed posting in this book.",
        List.of(
            detailField(
                "postingId",
                "Posting identifier supplied by the caller that does not exist in this book.")));

    private final String code;
    private final String description;
    private final List<ContractResponse.FieldDescriptor> detailFields;

    Descriptor(String code, String description) {
      this(code, description, List.of());
    }

    Descriptor(
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
      return Arrays.stream(values()).map(Descriptor::descriptor).toList();
    }
  }
}
