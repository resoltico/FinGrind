package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
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
import dev.erst.fingrind.executor.bookkeeping.policy.BookkeepingPolicyPack;
import dev.erst.fingrind.executor.bookkeeping.policy.CoreBookkeepingPolicyPack;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Local bookkeeping read/query service used before any public published-language projection. */
public final class BookkeepingReadService {
  private final BookkeepingReadStore bookStore;
  private final BookkeepingPolicyPack policyPack;
  private final BookkeepingStatementService statementService;

  /** Creates the local bookkeeping read service over one selected-book store seam. */
  public BookkeepingReadService(BookkeepingReadStore bookStore) {
    this(bookStore, CoreBookkeepingPolicyPack.current());
  }

  /**
   * Creates the local bookkeeping read service over one selected-book store seam and policy pack.
   */
  public BookkeepingReadService(BookkeepingReadStore bookStore, BookkeepingPolicyPack policyPack) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.policyPack = BookkeepingPolicyPack.requirePolicyPack(policyPack);
    this.statementService =
        new BookkeepingStatementService(this.bookStore, this.bookStore, this.policyPack);
  }

  /** Returns the local lifecycle snapshot before public contract projection. */
  public BookLifecycleInspection inspectBook() {
    return bookStore.inspectBook();
  }

  /** Looks up one declared account while preserving lifecycle rejection distinctly. */
  public BookkeepingLookupOutcome<RegisteredAccount> findAccount(AccountCode accountCode) {
    Objects.requireNonNull(accountCode, "accountCode");
    return lookupOutcome(() -> bookStore.findAccount(accountCode));
  }

  /** Looks up one committed posting while preserving lifecycle rejection distinctly. */
  public BookkeepingLookupOutcome<CommittedPosting> findPosting(PostingId postingId) {
    Objects.requireNonNull(postingId, "postingId");
    return lookupOutcome(() -> bookStore.findPosting(postingId));
  }

  /** Lists one paginated slice of the current account registry for the selected book. */
  public BookkeepingReadOutcome<AccountRegistryPage> listAccounts(AccountRegistryQuery query) {
    Objects.requireNonNull(query, "query");
    return ifInitializedOutcome(
        () -> new BookkeepingReadOutcome.Reported<>(bookStore.listAccounts(query)));
  }

  /** Returns one committed posting by durable posting identity. */
  public BookkeepingReadOutcome<CommittedPosting> getPosting(PostingId postingId) {
    Objects.requireNonNull(postingId, "postingId");
    return ifInitializedOutcome(
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
    return ifInitializedOutcome(
        () -> {
          Optional<BookkeepingQueryRejection> accountRejection =
              accountRejection(query.accountCode());
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
    return ifInitializedOutcome(
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
    return ifInitializedOutcome(
        () -> new BookkeepingReadOutcome.Reported<>(trialBalanceView(query)));
  }

  /** Computes one running ledger for the selected declared account. */
  public BookkeepingReadOutcome<AccountLedgerView> accountLedger(AccountLedgerCriteria query) {
    Objects.requireNonNull(query, "query");
    return ifInitializedOutcome(
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
    return ifInitializedOutcome(
        () -> new BookkeepingReadOutcome.Reported<>(bookStore.periodSummary(query)));
  }

  /** Computes one statement of financial position. */
  public BookkeepingReadOutcome<FinancialPositionView> financialPosition(
      FinancialPositionCriteria query) {
    Objects.requireNonNull(query, "query");
    return ifInitializedOutcome(
        () -> new BookkeepingReadOutcome.Reported<>(statementService.financialPosition(query)));
  }

  /** Computes one income statement for a bounded reporting period. */
  public BookkeepingReadOutcome<IncomeStatementView> incomeStatement(
      IncomeStatementCriteria query) {
    Objects.requireNonNull(query, "query");
    return ifInitializedOutcome(
        () -> new BookkeepingReadOutcome.Reported<>(statementService.incomeStatement(query)));
  }

  /** Computes one statement of changes in equity for a bounded reporting period. */
  public BookkeepingReadOutcome<ChangesInEquityView> changesInEquity(
      ChangesInEquityCriteria query) {
    Objects.requireNonNull(query, "query");
    return ifInitializedOutcome(
        () -> new BookkeepingReadOutcome.Reported<>(statementService.changesInEquity(query)));
  }

  private Optional<BookkeepingQueryRejection> accountRejection(Optional<AccountCode> accountCode) {
    if (accountCode.isPresent() && bookStore.findAccount(accountCode.orElseThrow()).isEmpty()) {
      return Optional.of(new BookkeepingQueryRejection.UnknownAccount(accountCode.orElseThrow()));
    }
    return Optional.empty();
  }

  private TrialBalanceView trialBalanceView(TrialBalanceCriteria query) {
    TrialBalanceView currentView = bookStore.trialBalance(query);
    var comparativeRange =
        policyPack
            .statementComparativePolicy()
            .comparativeAsOf(currentView.bookIdentity(), currentView.effectiveDateTo());
    return new TrialBalanceView(
        currentView.bookIdentity(),
        currentView.effectiveDateTo(),
        comparativeRange,
        currentView.postingCoverage(),
        currentView.rows(),
        comparativeRange.effectiveDateTo().isPresent()
            ? bookStore
                .trialBalance(
                    new TrialBalanceCriteria(
                        comparativeRange.effectiveDateTo(), query.postingCoverage()))
                .rows()
            : List.of());
  }

  private <T> BookkeepingReadOutcome<T> ifInitializedOutcome(
      Supplier<BookkeepingReadOutcome<T>> initializedAction) {
    if (!inspectBook().allowsInitializedWorkflow()) {
      return new BookkeepingReadOutcome.Rejected<>(
          new BookkeepingQueryRejection.BookNotInitialized());
    }
    return initializedAction.get();
  }

  private <T> BookkeepingLookupOutcome<T> lookupOutcome(Supplier<Optional<T>> initializedAction) {
    if (!inspectBook().allowsInitializedWorkflow()) {
      return new BookkeepingLookupOutcome.Rejected<>(
          new BookkeepingQueryRejection.BookNotInitialized());
    }
    return initializedAction
        .get()
        .<BookkeepingLookupOutcome<T>>map(BookkeepingLookupOutcome.Found::new)
        .orElseGet(BookkeepingLookupOutcome.Missing::new);
  }
}
