package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
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
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ -> "book-not-initialized";
      case BookQueryRejection.UnknownAccount _ -> "unknown-account";
      case BookQueryRejection.PostingNotFound _ -> "posting-not-found";
    };
  }

  /** Returns the canonical machine descriptors for every permitted query rejection. */
  static List<MachineContract.RejectionDescriptor> descriptors() {
    return List.of(
        new MachineContract.RejectionDescriptor(
            "book-not-initialized",
            "Query refused because the selected book does not exist or has not been initialized with "
                + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                + "."),
        new MachineContract.RejectionDescriptor(
            "unknown-account",
            "Query refused because the selected accountCode is not declared in this book."),
        new MachineContract.RejectionDescriptor(
            "posting-not-found",
            "Query refused because the selected postingId does not identify a committed posting in this book."));
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
}
