package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractRejectionDescriptors;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.FieldDescriptor;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
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
  static List<RejectionDescriptor> descriptors() {
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

  private static Descriptor descriptorFor(BookQueryRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookQueryRejection.BookNotInitialized _ -> Descriptor.BOOK_NOT_INITIALIZED;
      case BookQueryRejection.UnknownAccount _ -> Descriptor.UNKNOWN_ACCOUNT;
      case BookQueryRejection.PostingNotFound _ -> Descriptor.POSTING_NOT_FOUND;
    };
  }

  /** Canonical query rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_NOT_INITIALIZED(
        "query-book-not-initialized",
        "Query refused because the selected book does not exist or has not been initialized with "
            + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
            + ".") {
      @Override
      List<FieldDescriptor> detailFields() {
        return List.of();
      }
    },
    UNKNOWN_ACCOUNT(
        "unknown-account",
        "Query refused because the selected accountCode is not declared in this book.") {
      @Override
      List<FieldDescriptor> detailFields() {
        return List.of(
            ContractRejectionDescriptors.detailField(
                "accountCode",
                "Undeclared accountCode supplied by the caller for the rejected query."));
      }
    },
    POSTING_NOT_FOUND(
        "posting-not-found",
        "Query refused because the selected postingId does not identify a committed posting in this book.") {
      @Override
      List<FieldDescriptor> detailFields() {
        return List.of(
            ContractRejectionDescriptors.detailField(
                "postingId",
                "Posting identifier supplied by the caller that does not exist in this book."));
      }
    };

    private final String code;
    private final String description;

    Descriptor(String code, String description) {
      this.code = code;
      this.description = description;
    }

    private String code() {
      return code;
    }

    private static List<RejectionDescriptor> descriptors() {
      return ContractRejectionDescriptors.descriptors(values(), Descriptor::descriptor);
    }

    private RejectionDescriptor descriptor() {
      return ContractRejectionDescriptors.descriptor(code, category(), description, detailFields());
    }

    private FailureCategory category() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED -> FailureCategory.PRECONDITION;
        case UNKNOWN_ACCOUNT, POSTING_NOT_FOUND -> FailureCategory.DOMAIN_SEMANTIC;
      };
    }

    abstract List<FieldDescriptor> detailFields();
  }
}
