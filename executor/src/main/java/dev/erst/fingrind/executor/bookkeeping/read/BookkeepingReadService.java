package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import dev.erst.fingrind.executor.bookkeeping.CashFlowStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.CashFlowStatementView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityCriteria;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityView;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionCriteria;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.bookkeeping.reporting.BookkeepingReportingService;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping read/query service used before any public published-language projection. */
public final class BookkeepingReadService {
  private final BookkeepingReadStore bookStore;
  private final BookkeepingReportingService reportingService;

  /** Creates the local bookkeeping read service over one selected-book store seam. */
  public BookkeepingReadService(BookkeepingReadStore bookStore) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.reportingService = new BookkeepingReportingService(this.bookStore);
  }

  /** Returns the local lifecycle snapshot before public contract projection. */
  public BookLifecycleInspection inspectBook() {
    return bookStore.inspectBook();
  }

  /** Returns whether initialized-book workflows may proceed for the selected store. */
  public boolean allowsInitializedWorkflow() {
    return bookStore.allowsInitializedWorkflow();
  }

  /** Returns the selected initialized book identity or throws when the book is not initialized. */
  public BookIdentity requireInitializedBookIdentity() {
    return bookStore.requireInitializedBookIdentity();
  }

  /** Looks up one declared account while preserving lifecycle rejection distinctly. */
  public BookkeepingLookupOutcome<RegisteredAccount> findAccount(AccountCode accountCode) {
    Objects.requireNonNull(accountCode, "accountCode");
    return BookkeepingReadLifecycleGate.lookup(bookStore, () -> bookStore.findAccount(accountCode));
  }

  /** Looks up one committed posting while preserving lifecycle rejection distinctly. */
  public BookkeepingLookupOutcome<CommittedPosting> findPosting(PostingId postingId) {
    Objects.requireNonNull(postingId, "postingId");
    return BookkeepingReadLifecycleGate.lookup(bookStore, () -> bookStore.findPosting(postingId));
  }

  /** Lists one paginated slice of the current account registry for the selected book. */
  public BookkeepingReadOutcome<AccountRegistryPage> listAccounts(AccountRegistryQuery query) {
    Objects.requireNonNull(query, "query");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore, () -> new BookkeepingReadOutcome.Reported<>(bookStore.listAccounts(query)));
  }

  /** Returns one committed posting by durable posting identity. */
  public BookkeepingReadOutcome<CommittedPosting> getPosting(PostingId postingId) {
    Objects.requireNonNull(postingId, "postingId");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () ->
            bookStore
                .findPosting(postingId)
                .<BookkeepingReadOutcome<CommittedPosting>>map(BookkeepingReadOutcome.Reported::new)
                .orElseGet(
                    () ->
                        new BookkeepingReadOutcome.Rejected<>(
                            new BookkeepingQueryRejection.PostingNotFound(postingId))));
  }

  /** Returns one filtered page of committed postings. */
  public BookkeepingReadOutcome<PostingHistoryPage> listPostings(PostingHistoryQuery query) {
    Objects.requireNonNull(query, "query");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () -> {
          Optional<BookkeepingQueryRejection> accountRejection =
              BookkeepingReadQuerySupport.accountRejection(bookStore, query.accountCode());
          if (accountRejection.isPresent()) {
            return new BookkeepingReadOutcome.Rejected<PostingHistoryPage>(
                accountRejection.orElseThrow());
          }
          return new BookkeepingReadOutcome.Reported<>(bookStore.listPostings(query));
        });
  }

  /** Computes one grouped per-currency balance snapshot for the selected declared account. */
  public BookkeepingReadOutcome<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    Objects.requireNonNull(query, "query");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () ->
            bookStore
                .accountBalance(query)
                .<BookkeepingReadOutcome<AccountBalanceView>>map(
                    BookkeepingReadOutcome.Reported::new)
                .orElseGet(
                    () ->
                        new BookkeepingReadOutcome.Rejected<>(
                            new BookkeepingQueryRejection.UnknownAccount(query.accountCode()))));
  }

  /** Computes one book-wide trial balance. */
  public BookkeepingReadOutcome<TrialBalanceView> trialBalance(TrialBalanceCriteria query) {
    Objects.requireNonNull(query, "query");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () ->
            new BookkeepingReadOutcome.Reported<>(
                BookkeepingReadQuerySupport.trialBalanceView(bookStore, query)));
  }

  /** Computes one running ledger for the selected declared account. */
  public BookkeepingReadOutcome<AccountLedgerView> accountLedger(AccountLedgerCriteria query) {
    Objects.requireNonNull(query, "query");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () -> {
          Optional<RegisteredAccount> account = bookStore.findAccount(query.accountCode());
          if (account.isEmpty()) {
            return new BookkeepingReadOutcome.Rejected<AccountLedgerView>(
                new BookkeepingQueryRejection.UnknownAccount(query.accountCode()));
          }
          return new BookkeepingReadOutcome.Reported<>(
              bookStore.accountLedger(query, account.orElseThrow()));
        });
  }

  /** Computes one bounded period summary for the selected book. */
  public BookkeepingReadOutcome<PeriodSummaryView> periodSummary(PeriodSummaryCriteria query) {
    Objects.requireNonNull(query, "query");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore, () -> new BookkeepingReadOutcome.Reported<>(bookStore.periodSummary(query)));
  }

  /** Computes one statement of financial position. */
  public BookkeepingReadOutcome<FinancialPositionView> financialPosition(
      FinancialPositionCriteria query) {
    Objects.requireNonNull(query, "query");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () -> new BookkeepingReadOutcome.Reported<>(reportingService.financialPosition(query)));
  }

  /** Computes one income statement for a bounded reporting period. */
  public BookkeepingReadOutcome<IncomeStatementView> incomeStatement(
      IncomeStatementCriteria query) {
    Objects.requireNonNull(query, "query");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () -> new BookkeepingReadOutcome.Reported<>(reportingService.incomeStatement(query)));
  }

  /** Computes one statement of cash receipts and payments for a bounded reporting period. */
  public BookkeepingReadOutcome<CashFlowStatementView> cashFlowStatement(
      CashFlowStatementCriteria query) {
    Objects.requireNonNull(query, "query");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () -> new BookkeepingReadOutcome.Reported<>(reportingService.cashFlowStatement(query)));
  }

  /** Computes one statement of changes in equity for a bounded reporting period. */
  public BookkeepingReadOutcome<ChangesInEquityView> changesInEquity(
      ChangesInEquityCriteria query) {
    Objects.requireNonNull(query, "query");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () -> new BookkeepingReadOutcome.Reported<>(reportingService.changesInEquity(query)));
  }
}
