package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;

/** Local bookkeeping refusal family for query and report commands. */
public sealed interface BookkeepingQueryRejection
    permits BookkeepingQueryRejection.BookNotInitialized,
        BookkeepingQueryRejection.UnknownAccount,
        BookkeepingQueryRejection.PostingNotFound {

  /** Stable local code for this bookkeeping query rejection instance. */
  String localCode();

  /** Stable local code for one bookkeeping query rejection instance. */
  static String wireCode(BookkeepingQueryRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return rejection.localCode();
  }

  /** Stable local code for the missing-book bookkeeping query rejection. */
  static String bookNotInitializedCode() {
    return "query-book-not-initialized";
  }

  /** Refusal for a query against a missing or uninitialized book. */
  record BookNotInitialized() implements BookkeepingQueryRejection {
    @Override
    public String localCode() {
      return "query-book-not-initialized";
    }
  }

  /** Refusal for a query that names an undeclared account. */
  record UnknownAccount(AccountCode accountCode) implements BookkeepingQueryRejection {
    public UnknownAccount {
      Objects.requireNonNull(accountCode, "accountCode");
    }

    @Override
    public String localCode() {
      return "unknown-account";
    }
  }

  /** Refusal for a query that names a posting that does not exist in the selected book. */
  record PostingNotFound(PostingId postingId) implements BookkeepingQueryRejection {
    public PostingNotFound {
      Objects.requireNonNull(postingId, "postingId");
    }

    @Override
    public String localCode() {
      return "posting-not-found";
    }
  }
}
