package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
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
    return switch (listAccountsOutcome(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookReadOutcome.Reported<AccountRegistryPage> reported ->
          new ListAccountsResult.Listed(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookReadOutcome.Rejected<AccountRegistryPage> rejected ->
          new ListAccountsResult.Rejected(rejected.rejection());
    };
  }

  /** Returns one committed posting by durable posting identity. */
  public GetPostingResult getPosting(PostingId postingId) {
    return switch (getPostingOutcome(postingId)) {
      case BookReadOutcome.Reported<CommittedPosting> reported ->
          new GetPostingResult.Found(
              BookkeepingPublishedLanguageTranslator.toPublished(reported.value()));
      case BookReadOutcome.Rejected<CommittedPosting> rejected ->
          new GetPostingResult.Rejected(rejected.rejection());
    };
  }

  /** Looks up one declared account once the selected book is known to be initialized. */
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    Objects.requireNonNull(accountCode, "accountCode");
    if (!bookReadSession.isInitialized()) {
      return Optional.empty();
    }
    return bookReadSession.findAccount(accountCode);
  }

  /** Looks up one committed posting once the selected book is known to be initialized. */
  public Optional<CommittedPosting> findPosting(PostingId postingId) {
    Objects.requireNonNull(postingId, "postingId");
    if (!bookReadSession.isInitialized()) {
      return Optional.empty();
    }
    return bookReadSession.findPosting(postingId);
  }

  /** Returns one filtered page of committed postings. */
  public ListPostingsResult listPostings(ListPostingsQuery query) {
    return switch (listPostingsOutcome(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookReadOutcome.Reported<PostingHistoryPage> reported ->
          new ListPostingsResult.Listed(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookReadOutcome.Rejected<PostingHistoryPage> rejected ->
          new ListPostingsResult.Rejected(rejected.rejection());
    };
  }

  /** Computes one grouped per-currency balance snapshot for the selected declared account. */
  public AccountBalanceResult accountBalance(AccountBalanceQuery query) {
    return switch (accountBalanceOutcome(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookReadOutcome.Reported<AccountBalanceView> reported ->
          new AccountBalanceResult.Reported(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookReadOutcome.Rejected<AccountBalanceView> rejected ->
          new AccountBalanceResult.Rejected(rejected.rejection());
    };
  }

  /** Computes one book-wide trial balance. */
  public TrialBalanceResult trialBalance(TrialBalanceQuery query) {
    return switch (trialBalanceOutcome(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookReadOutcome.Reported<TrialBalanceView> reported ->
          new TrialBalanceResult.Reported(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookReadOutcome.Rejected<TrialBalanceView> rejected ->
          new TrialBalanceResult.Rejected(rejected.rejection());
    };
  }

  /** Computes one running ledger for the selected declared account. */
  public AccountLedgerResult accountLedger(AccountLedgerQuery query) {
    return switch (accountLedgerOutcome(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookReadOutcome.Reported<AccountLedgerView> reported ->
          new AccountLedgerResult.Reported(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookReadOutcome.Rejected<AccountLedgerView> rejected ->
          new AccountLedgerResult.Rejected(rejected.rejection());
    };
  }

  /** Computes one bounded period summary for the selected book. */
  public PeriodSummaryResult periodSummary(PeriodSummaryQuery query) {
    return switch (periodSummaryOutcome(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookReadOutcome.Reported<PeriodSummaryView> reported ->
          new PeriodSummaryResult.Reported(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookReadOutcome.Rejected<PeriodSummaryView> rejected ->
          new PeriodSummaryResult.Rejected(rejected.rejection());
    };
  }

  BookReadOutcome<AccountRegistryPage> listAccountsOutcome(AccountRegistryQuery query) {
    Objects.requireNonNull(query, "query");
    return ifInitializedOutcome(
        () -> new BookReadOutcome.Reported<>(bookReadSession.listAccounts(query)));
  }

  BookReadOutcome<CommittedPosting> getPostingOutcome(PostingId postingId) {
    Objects.requireNonNull(postingId, "postingId");
    return ifInitializedOutcome(
        () ->
            bookReadSession
                .findPosting(postingId)
                .<BookReadOutcome<CommittedPosting>>map(BookReadOutcome.Reported::new)
                .orElseGet(
                    () ->
                        new BookReadOutcome.Rejected<>(
                            new BookQueryRejection.PostingNotFound(postingId))));
  }

  BookReadOutcome<PostingHistoryPage> listPostingsOutcome(PostingHistoryQuery query) {
    Objects.requireNonNull(query, "query");
    return ifInitializedOutcome(
        () -> {
          Optional<BookQueryRejection> accountRejection = accountRejection(query.accountCode());
          if (accountRejection.isPresent()) {
            return new BookReadOutcome.Rejected<PostingHistoryPage>(accountRejection.orElseThrow());
          }
          return new BookReadOutcome.Reported<>(bookReadSession.listPostings(query));
        });
  }

  BookReadOutcome<AccountBalanceView> accountBalanceOutcome(AccountBalanceCriteria query) {
    Objects.requireNonNull(query, "query");
    return ifInitializedOutcome(
        () ->
            bookReadSession
                .accountBalance(query)
                .<BookReadOutcome<AccountBalanceView>>map(BookReadOutcome.Reported::new)
                .orElseGet(
                    () ->
                        new BookReadOutcome.Rejected<>(
                            new BookQueryRejection.UnknownAccount(query.accountCode()))));
  }

  BookReadOutcome<TrialBalanceView> trialBalanceOutcome(TrialBalanceCriteria query) {
    Objects.requireNonNull(query, "query");
    return ifInitializedOutcome(
        () -> new BookReadOutcome.Reported<>(bookReadSession.trialBalance(query)));
  }

  BookReadOutcome<AccountLedgerView> accountLedgerOutcome(AccountLedgerCriteria query) {
    Objects.requireNonNull(query, "query");
    return ifInitializedOutcome(
        () -> {
          Optional<RegisteredAccount> account = bookReadSession.findAccount(query.accountCode());
          if (account.isEmpty()) {
            return new BookReadOutcome.Rejected<AccountLedgerView>(
                new BookQueryRejection.UnknownAccount(query.accountCode()));
          }
          return new BookReadOutcome.Reported<>(
              bookReadSession.accountLedger(query, account.orElseThrow()));
        });
  }

  BookReadOutcome<PeriodSummaryView> periodSummaryOutcome(PeriodSummaryCriteria query) {
    Objects.requireNonNull(query, "query");
    return ifInitializedOutcome(
        () -> new BookReadOutcome.Reported<>(bookReadSession.periodSummary(query)));
  }

  private Optional<BookQueryRejection> accountRejection(Optional<AccountCode> accountCode) {
    if (accountCode.isPresent()
        && bookReadSession.findAccount(accountCode.orElseThrow()).isEmpty()) {
      return Optional.of(new BookQueryRejection.UnknownAccount(accountCode.orElseThrow()));
    }
    return Optional.empty();
  }

  private <T> BookReadOutcome<T> ifInitializedOutcome(
      java.util.function.Supplier<BookReadOutcome<T>> initializedAction) {
    if (!bookReadSession.isInitialized()) {
      return new BookReadOutcome.Rejected<>(new BookQueryRejection.BookNotInitialized());
    }
    return initializedAction.get();
  }
}
