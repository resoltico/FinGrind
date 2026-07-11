package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationAccount;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationMovement;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationReport;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadPagePublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadReportPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadStatementPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingInventoryReadService;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.Objects;
import java.util.function.Function;

/** Application service that owns every read-only book workflow behind one unified seam. */
public final class BookReadService {
  private final BookkeepingReadStore bookStore;
  private final BookkeepingReadService bookkeepingReadService;
  private final BookkeepingInventoryReadService bookkeepingInventoryReadService;

  /** Creates the read service with its application-owned inspection, query, and report seam. */
  public BookReadService(BookkeepingReadStore bookStore) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.bookkeepingReadService = new BookkeepingReadService(this.bookStore);
    this.bookkeepingInventoryReadService = new BookkeepingInventoryReadService(this.bookStore);
  }

  /** Inspects the selected book file without mutating it. */
  public BookInspection inspectBook() {
    return BookReadInspectionProjection.project(bookStore, bookkeepingReadService.inspectBook());
  }

  /** Lists one paginated slice of the current account registry for the selected book. */
  public ListAccountsResult listAccounts(ListAccountsQuery query) {
    return switch (bookkeepingReadService.listAccounts(
        BookReadQueryTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<AccountRegistryPage> reported ->
          new ListAccountsResult.Listed(
              BookkeepingReadPagePublishedLanguageTranslator.toPublished(
                  bookkeepingReadService.requireInitializedBookIdentity(), reported.value()));
      case BookkeepingReadOutcome.Rejected<AccountRegistryPage> rejected ->
          new ListAccountsResult.Rejected(mapReadOutcomeRejection(rejected.rejection()));
    };
  }

  /** Returns one committed posting by durable posting identity. */
  public GetPostingResult getPosting(PostingId postingId) {
    BookkeepingReadOutcome<CommittedPosting> outcome = bookkeepingReadService.getPosting(postingId);
    if (outcome instanceof BookkeepingReadOutcome.Reported<CommittedPosting> reported) {
      return new GetPostingResult.Found(
          bookkeepingReadService.requireInitializedBookIdentity(),
          BookkeepingPublishedLanguageTranslator.toPublished(reported.value()),
          bookStore.findReversalFor(postingId).map(CommittedPosting::postingId));
    }
    BookkeepingReadOutcome.Rejected<CommittedPosting> rejected =
        (BookkeepingReadOutcome.Rejected<CommittedPosting>) outcome;
    return new GetPostingResult.Rejected(mapReadOutcomeRejection(rejected.rejection()));
  }

  /** Returns one filtered page of committed postings. */
  public ListPostingsResult listPostings(ListPostingsQuery query) {
    PostingHistoryQuery publishedQuery = BookReadQueryTranslator.fromPublished(query);
    return switch (bookkeepingReadService.listPostings(publishedQuery)) {
      case BookkeepingReadOutcome.Reported<PostingHistoryPage> reported ->
          new ListPostingsResult.Listed(
              BookReadPostingBacklinkProjection.withReversalBacklinks(
                  bookStore,
                  BookkeepingReadPagePublishedLanguageTranslator.toPublished(
                      bookkeepingReadService.requireInitializedBookIdentity(),
                      publishedQuery,
                      reported.value()),
                  reported.value()));
      case BookkeepingReadOutcome.Rejected<PostingHistoryPage> rejected ->
          new ListPostingsResult.Rejected(mapReadOutcomeRejection(rejected.rejection()));
    };
  }

  /** Computes one grouped per-currency balance snapshot for the selected declared account. */
  public AccountBalanceResult accountBalance(AccountBalanceQuery query) {
    return mapReadOutcome(
        bookkeepingReadService.accountBalance(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new AccountBalanceResult.Reported(
                BookkeepingReadPagePublishedLanguageTranslator.toPublished(
                    bookkeepingReadService.requireInitializedBookIdentity(), value)),
        AccountBalanceResult.Rejected::new);
  }

  /** Computes one book-wide trial balance. */
  public TrialBalanceResult trialBalance(TrialBalanceQuery query) {
    return mapReadOutcome(
        bookkeepingReadService.trialBalance(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new TrialBalanceResult.Reported(
                BookkeepingReadReportPublishedLanguageTranslator.toPublished(value)),
        TrialBalanceResult.Rejected::new);
  }

  /** Computes one running ledger for the selected declared account. */
  public AccountLedgerResult accountLedger(AccountLedgerQuery query) {
    return mapReadOutcome(
        bookkeepingReadService.accountLedger(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new AccountLedgerResult.Reported(
                BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                    bookkeepingReadService.requireInitializedBookIdentity(), value)),
        AccountLedgerResult.Rejected::new);
  }

  /** Computes one bounded period summary for the selected book. */
  public PeriodSummaryResult periodSummary(PeriodSummaryQuery query) {
    return mapReadOutcome(
        bookkeepingReadService.periodSummary(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new PeriodSummaryResult.Reported(
                BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                    bookkeepingReadService.requireInitializedBookIdentity(), value)),
        PeriodSummaryResult.Rejected::new);
  }

  /** Computes one statement of financial position for the selected book. */
  public FinancialPositionResult financialPosition(FinancialPositionQuery query) {
    return mapReadOutcome(
        bookkeepingReadService.financialPosition(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new FinancialPositionResult.Reported(
                BookkeepingReadStatementPublishedLanguageTranslator.toPublished(value)),
        FinancialPositionResult.Rejected::new);
  }

  /** Computes one income statement for the selected book and reporting period. */
  public IncomeStatementResult incomeStatement(IncomeStatementQuery query) {
    return mapReadOutcome(
        bookkeepingReadService.incomeStatement(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new IncomeStatementResult.Reported(
                BookkeepingReadStatementPublishedLanguageTranslator.toPublished(value)),
        IncomeStatementResult.Rejected::new);
  }

  /** Computes exact inventory carrying values from canonical durable movement replay. */
  public InventoryValuationResult inventoryValuation(InventoryValuationQuery query) {
    return mapReadOutcome(
        bookkeepingInventoryReadService.inventoryValuation(
            BookReadQueryTranslator.fromPublished(query)),
        values ->
            new InventoryValuationResult.Reported(
                new InventoryValuationReport(
                    bookkeepingReadService.requireInitializedBookIdentity(),
                    query.effectiveDateAsOf(),
                    query.includeMovements(),
                    values.stream().map(BookReadService::toPublishedInventoryValuation).toList())),
        InventoryValuationResult.Rejected::new);
  }

  /** Computes one statement of cash receipts and payments for the selected book and period. */
  public CashFlowStatementResult cashFlowStatement(CashFlowStatementQuery query) {
    return mapReadOutcome(
        bookkeepingReadService.cashFlowStatement(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new CashFlowStatementResult.Reported(
                BookkeepingReadStatementPublishedLanguageTranslator.toPublished(value)),
        CashFlowStatementResult.Rejected::new);
  }

  /** Computes one statement of changes in equity for the selected book and reporting period. */
  public ChangesInEquityResult changesInEquity(ChangesInEquityQuery query) {
    return mapReadOutcome(
        bookkeepingReadService.changesInEquity(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new ChangesInEquityResult.Reported(
                BookkeepingReadStatementPublishedLanguageTranslator.toPublished(value)),
        ChangesInEquityResult.Rejected::new);
  }

  private static <V, R> R mapReadOutcome(
      BookkeepingReadOutcome<V> outcome,
      Function<V, R> reportedMapper,
      Function<BookQueryRejection, R> rejectedMapper) {
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(reportedMapper, "reportedMapper");
    Objects.requireNonNull(rejectedMapper, "rejectedMapper");
    if (outcome instanceof BookkeepingReadOutcome.Reported<V> reported) {
      return reportedMapper.apply(reported.value());
    }
    BookkeepingReadOutcome.Rejected<V> rejected = (BookkeepingReadOutcome.Rejected<V>) outcome;
    return rejectedMapper.apply(mapReadOutcomeRejection(rejected.rejection()));
  }

  private static InventoryValuationAccount toPublishedInventoryValuation(
      InventoryValuationView valuation) {
    return new InventoryValuationAccount(
        valuation.account().accountCode(),
        valuation.account().accountName(),
        Objects.requireNonNull(valuation.account().unitOfMeasure(), "inventory unitOfMeasure"),
        valuation.pool().quantityOnHand(),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(valuation.pool().costPool()),
        valuation.roundedMovingAverageUnitCostProjection() == null
            ? null
            : dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                valuation.roundedMovingAverageUnitCostProjection()),
        valuation.movements().stream()
            .map(
                movement ->
                    new InventoryValuationMovement(
                        movement.postingId(),
                        movement.effectiveDate(),
                        movement.accountSequence(),
                        movement.kind(),
                        movement.quantityDelta(),
                        movement.costDeltaMinor()))
            .toList());
  }

  private static BookQueryRejection mapReadOutcomeRejection(
      dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return switch (rejection) {
      case dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection.BookNotInitialized _ ->
          new BookQueryRejection.BookNotInitialized();
      case dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection.UnknownAccount
              unknownAccount ->
          new BookQueryRejection.UnknownAccount(unknownAccount.accountCode());
      case dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection.PostingNotFound
              postingNotFound ->
          new BookQueryRejection.PostingNotFound(postingNotFound.postingId());
    };
  }
}
