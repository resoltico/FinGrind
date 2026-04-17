package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;
import java.util.Optional;

/** Application service that owns read-only inspection, listing, and balance workflows. */
public final class BookQueryService {
  private final BookQuerySession bookQuerySession;

  /** Creates the query service with its application-owned read seam. */
  public BookQueryService(BookQuerySession bookQuerySession) {
    this.bookQuerySession = Objects.requireNonNull(bookQuerySession, "bookQuerySession");
  }

  /** Inspects the selected book file without mutating it. */
  public BookInspection inspectBook() {
    return bookQuerySession.inspectBook();
  }

  /** Lists one paginated slice of the current account registry for the selected book. */
  public ListAccountsResult listAccounts(ListAccountsQuery query) {
    Objects.requireNonNull(query, "query");
    if (!bookQuerySession.isInitialized()) {
      return new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized());
    }
    return new ListAccountsResult.Listed(bookQuerySession.listAccounts(query));
  }

  /** Returns one committed posting by durable posting identity. */
  public GetPostingResult getPosting(PostingId postingId) {
    Objects.requireNonNull(postingId, "postingId");
    if (!bookQuerySession.isInitialized()) {
      return new GetPostingResult.Rejected(new BookQueryRejection.BookNotInitialized());
    }
    return bookQuerySession
        .findPosting(postingId)
        .<GetPostingResult>map(GetPostingResult.Found::new)
        .orElseGet(
            () -> new GetPostingResult.Rejected(new BookQueryRejection.PostingNotFound(postingId)));
  }

  /** Returns one filtered page of committed postings. */
  public ListPostingsResult listPostings(ListPostingsQuery query) {
    Objects.requireNonNull(query, "query");
    if (!bookQuerySession.isInitialized()) {
      return new ListPostingsResult.Rejected(new BookQueryRejection.BookNotInitialized());
    }
    Optional<BookQueryRejection> accountRejection = accountRejection(query.accountCode());
    if (accountRejection.isPresent()) {
      return new ListPostingsResult.Rejected(accountRejection.orElseThrow());
    }
    return new ListPostingsResult.Listed(bookQuerySession.listPostings(query));
  }

  /** Computes one grouped per-currency balance snapshot for the selected declared account. */
  public AccountBalanceResult accountBalance(AccountBalanceQuery query) {
    Objects.requireNonNull(query, "query");
    if (!bookQuerySession.isInitialized()) {
      return new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized());
    }
    Optional<BookQueryRejection> accountRejection =
        accountRejection(Optional.of(query.accountCode()));
    if (accountRejection.isPresent()) {
      return new AccountBalanceResult.Rejected(accountRejection.orElseThrow());
    }
    return new AccountBalanceResult.Reported(bookQuerySession.accountBalance(query));
  }

  private Optional<BookQueryRejection> accountRejection(Optional<AccountCode> accountCode) {
    if (accountCode.isPresent()
        && bookQuerySession.findAccount(accountCode.orElseThrow()).isEmpty()) {
      return Optional.of(new BookQueryRejection.UnknownAccount(accountCode.orElseThrow()));
    }
    return Optional.empty();
  }
}
