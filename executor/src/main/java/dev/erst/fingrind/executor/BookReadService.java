package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;
import java.util.Optional;

/** Application service that owns every read-only book workflow behind one unified seam. */
public final class BookReadService {
  private final BookReadSession bookReadSession;

  /** Creates the read service with its application-owned inspection, query, and report seam. */
  public BookReadService(BookReadSession bookReadSession) {
    this.bookReadSession = Objects.requireNonNull(bookReadSession, "bookReadSession");
  }

  /** Inspects the selected book file without mutating it. */
  public BookInspection inspectBook() {
    return bookReadSession.inspectBook();
  }

  /** Reports whether the selected book is initialized for read/write workflows. */
  public boolean isInitialized() {
    return bookReadSession.isInitialized();
  }

  /** Lists one paginated slice of the current account registry for the selected book. */
  public ListAccountsResult listAccounts(ListAccountsQuery query) {
    Objects.requireNonNull(query, "query");
    return ifInitialized(
        () -> new ListAccountsResult.Listed(bookReadSession.listAccounts(query)),
        ListAccountsResult.Rejected::new);
  }

  /** Returns one committed posting by durable posting identity. */
  public GetPostingResult getPosting(PostingId postingId) {
    Objects.requireNonNull(postingId, "postingId");
    return ifInitialized(
        () ->
            bookReadSession
                .findPosting(postingId)
                .<GetPostingResult>map(GetPostingResult.Found::new)
                .orElseGet(
                    () ->
                        new GetPostingResult.Rejected(
                            new BookQueryRejection.PostingNotFound(postingId))),
        GetPostingResult.Rejected::new);
  }

  /** Looks up one declared account once the selected book is known to be initialized. */
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    Objects.requireNonNull(accountCode, "accountCode");
    if (!bookReadSession.isInitialized()) {
      return Optional.empty();
    }
    return bookReadSession.findAccount(accountCode);
  }

  /** Looks up one committed posting once the selected book is known to be initialized. */
  public Optional<PostingFact> findPosting(PostingId postingId) {
    Objects.requireNonNull(postingId, "postingId");
    if (!bookReadSession.isInitialized()) {
      return Optional.empty();
    }
    return bookReadSession.findPosting(postingId);
  }

  /** Returns one filtered page of committed postings. */
  public ListPostingsResult listPostings(ListPostingsQuery query) {
    Objects.requireNonNull(query, "query");
    return ifInitialized(
        () -> {
          Optional<BookQueryRejection> accountRejection = accountRejection(query.accountCode());
          if (accountRejection.isPresent()) {
            return new ListPostingsResult.Rejected(accountRejection.orElseThrow());
          }
          return new ListPostingsResult.Listed(bookReadSession.listPostings(query));
        },
        ListPostingsResult.Rejected::new);
  }

  /** Computes one grouped per-currency balance snapshot for the selected declared account. */
  public AccountBalanceResult accountBalance(AccountBalanceQuery query) {
    Objects.requireNonNull(query, "query");
    return ifInitialized(
        () ->
            bookReadSession
                .accountBalance(query)
                .<AccountBalanceResult>map(AccountBalanceResult.Reported::new)
                .orElseGet(
                    () ->
                        new AccountBalanceResult.Rejected(
                            new BookQueryRejection.UnknownAccount(query.accountCode()))),
        AccountBalanceResult.Rejected::new);
  }

  /** Computes one book-wide trial balance. */
  public TrialBalanceResult trialBalance(TrialBalanceQuery query) {
    Objects.requireNonNull(query, "query");
    return ifInitialized(
        () -> new TrialBalanceResult.Reported(bookReadSession.trialBalance(query)),
        TrialBalanceResult.Rejected::new);
  }

  /** Computes one running ledger for the selected declared account. */
  public AccountLedgerResult accountLedger(AccountLedgerQuery query) {
    Objects.requireNonNull(query, "query");
    return ifInitialized(
        () -> {
          Optional<DeclaredAccount> account = bookReadSession.findAccount(query.accountCode());
          if (account.isEmpty()) {
            return new AccountLedgerResult.Rejected(
                new BookQueryRejection.UnknownAccount(query.accountCode()));
          }
          return new AccountLedgerResult.Reported(
              bookReadSession.accountLedger(query, account.orElseThrow()));
        },
        AccountLedgerResult.Rejected::new);
  }

  /** Computes one bounded period summary for the selected book. */
  public PeriodSummaryResult periodSummary(PeriodSummaryQuery query) {
    Objects.requireNonNull(query, "query");
    return ifInitialized(
        () -> new PeriodSummaryResult.Reported(bookReadSession.periodSummary(query)),
        PeriodSummaryResult.Rejected::new);
  }

  private Optional<BookQueryRejection> accountRejection(Optional<AccountCode> accountCode) {
    if (accountCode.isPresent()
        && bookReadSession.findAccount(accountCode.orElseThrow()).isEmpty()) {
      return Optional.of(new BookQueryRejection.UnknownAccount(accountCode.orElseThrow()));
    }
    return Optional.empty();
  }

  private <R> R ifInitialized(
      java.util.function.Supplier<R> initializedAction,
      java.util.function.Function<BookQueryRejection, R> rejectionFactory) {
    if (!bookReadSession.isInitialized()) {
      return rejectionFactory.apply(new BookQueryRejection.BookNotInitialized());
    }
    return initializedAction.get();
  }
}
