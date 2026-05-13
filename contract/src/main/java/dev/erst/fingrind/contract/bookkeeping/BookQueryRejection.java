package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
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
  enum Descriptor {
    BOOK_NOT_INITIALIZED,
    UNKNOWN_ACCOUNT,
    POSTING_NOT_FOUND;

    private String code() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED -> "query-book-not-initialized";
        case UNKNOWN_ACCOUNT -> "unknown-account";
        case POSTING_NOT_FOUND -> "posting-not-found";
      };
    }

    private String description() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED ->
            "Query refused because the selected book does not exist or has not been initialized with "
                + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                + ".";
        case UNKNOWN_ACCOUNT ->
            "Query refused because the selected accountCode is not declared in this book.";
        case POSTING_NOT_FOUND ->
            "Query refused because the selected postingId does not identify a committed posting in this book.";
      };
    }

    private List<ContractResponse.FieldDescriptor> detailFields() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED -> List.of();
        case UNKNOWN_ACCOUNT ->
            List.of(
                detailField(
                    "accountCode",
                    "Undeclared accountCode supplied by the caller for the rejected query."));
        case POSTING_NOT_FOUND ->
            List.of(
                detailField(
                    "postingId",
                    "Posting identifier supplied by the caller that does not exist in this book."));
      };
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(
          code(), description(), detailFields(), List.of());
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return List.of(BOOK_NOT_INITIALIZED, UNKNOWN_ACCOUNT, POSTING_NOT_FOUND).stream()
          .map(Descriptor::descriptor)
          .toList();
    }
  }
}
