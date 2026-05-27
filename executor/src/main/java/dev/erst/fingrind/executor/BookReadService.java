package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AcceptedResultHoldingSelection;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadPagePublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadReportPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadStatementPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.RejectedResultHoldingSelection;
import dev.erst.fingrind.executor.bookkeeping.ResultHoldingSelection;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.List;
import java.util.Objects;

/** Application service that owns every read-only book workflow behind one unified seam. */
public final class BookReadService {
  private final BookkeepingReadService bookkeepingReadService;

  /** Creates the read service with its application-owned inspection, query, and report seam. */
  public BookReadService(BookkeepingReadStore bookStore) {
    this.bookkeepingReadService =
        new BookkeepingReadService(Objects.requireNonNull(bookStore, "bookStore"));
  }

  /** Inspects the selected book file without mutating it. */
  public BookInspection inspectBook() {
    var inspection = bookkeepingReadService.inspectBook();
    if (inspection
        instanceof dev.erst.fingrind.executor.spi.BookLifecycleInspection.Initialized initialized) {
      return new BookInspection.Initialized(
          initialized.applicationId(),
          initialized.detectedBookFormatVersion(),
          initialized.supportedBookFormatVersion(),
          initialized.initializedAt(),
          initialized.bookIdentity(),
          resultTransferReadiness(initialized.bookIdentity()));
    }
    return BookInspectionPublishedLanguageTranslator.toPublished(inspection);
  }

  /** Lists one paginated slice of the current account registry for the selected book. */
  public ListAccountsResult listAccounts(ListAccountsQuery query) {
    return switch (bookkeepingReadService.listAccounts(
        BookReadQueryTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<AccountRegistryPage> reported ->
          new ListAccountsResult.Listed(
              BookkeepingReadPagePublishedLanguageTranslator.toPublished(
                  currentBookIdentity(), reported.value()));
      case BookkeepingReadOutcome.Rejected<AccountRegistryPage> rejected ->
          new ListAccountsResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Returns one committed posting by durable posting identity. */
  public GetPostingResult getPosting(PostingId postingId) {
    BookkeepingReadOutcome<CommittedPosting> outcome = bookkeepingReadService.getPosting(postingId);
    if (outcome instanceof BookkeepingReadOutcome.Reported<CommittedPosting> reported) {
      return new GetPostingResult.Found(
          currentBookIdentity(),
          BookkeepingPublishedLanguageTranslator.toPublished(reported.value()));
    }
    BookkeepingReadOutcome.Rejected<CommittedPosting> rejected =
        (BookkeepingReadOutcome.Rejected<CommittedPosting>) outcome;
    return new GetPostingResult.Rejected(toPublished(rejected.rejection()));
  }

  /** Returns one filtered page of committed postings. */
  public ListPostingsResult listPostings(ListPostingsQuery query) {
    var publishedQuery = BookReadQueryTranslator.fromPublished(query);
    return switch (bookkeepingReadService.listPostings(publishedQuery)) {
      case BookkeepingReadOutcome.Reported<PostingHistoryPage> reported ->
          new ListPostingsResult.Listed(
              BookkeepingReadPagePublishedLanguageTranslator.toPublished(
                  currentBookIdentity(), publishedQuery, reported.value()));
      case BookkeepingReadOutcome.Rejected<PostingHistoryPage> rejected ->
          new ListPostingsResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Computes one grouped per-currency balance snapshot for the selected declared account. */
  public AccountBalanceResult accountBalance(AccountBalanceQuery query) {
    return switch (bookkeepingReadService.accountBalance(
        BookReadQueryTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<
                  dev.erst.fingrind.executor.bookkeeping.AccountBalanceView>
              reported ->
          new AccountBalanceResult.Reported(
              BookkeepingReadPagePublishedLanguageTranslator.toPublished(
                  currentBookIdentity(), reported.value()));
      case BookkeepingReadOutcome.Rejected<
                  dev.erst.fingrind.executor.bookkeeping.AccountBalanceView>
              rejected ->
          new AccountBalanceResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Computes one book-wide trial balance. */
  public TrialBalanceResult trialBalance(TrialBalanceQuery query) {
    return switch (bookkeepingReadService.trialBalance(
        BookReadQueryTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<dev.erst.fingrind.executor.bookkeeping.TrialBalanceView>
              reported ->
          new TrialBalanceResult.Reported(
              BookkeepingReadReportPublishedLanguageTranslator.toPublished(reported.value()));
      case BookkeepingReadOutcome.Rejected<dev.erst.fingrind.executor.bookkeeping.TrialBalanceView>
              rejected ->
          new TrialBalanceResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Computes one running ledger for the selected declared account. */
  public AccountLedgerResult accountLedger(AccountLedgerQuery query) {
    return switch (bookkeepingReadService.accountLedger(
        BookReadQueryTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<dev.erst.fingrind.executor.bookkeeping.AccountLedgerView>
              reported ->
          new AccountLedgerResult.Reported(
              BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                  currentBookIdentity(), reported.value()));
      case BookkeepingReadOutcome.Rejected<dev.erst.fingrind.executor.bookkeeping.AccountLedgerView>
              rejected ->
          new AccountLedgerResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Computes one bounded period summary for the selected book. */
  public PeriodSummaryResult periodSummary(PeriodSummaryQuery query) {
    return switch (bookkeepingReadService.periodSummary(
        BookReadQueryTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView>
              reported ->
          new PeriodSummaryResult.Reported(
              BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                  currentBookIdentity(), reported.value()));
      case BookkeepingReadOutcome.Rejected<dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView>
              rejected ->
          new PeriodSummaryResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Computes one statement of financial position for the selected book. */
  public FinancialPositionResult financialPosition(FinancialPositionQuery query) {
    return switch (bookkeepingReadService.financialPosition(
        BookReadQueryTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<
                  dev.erst.fingrind.executor.bookkeeping.FinancialPositionView>
              reported ->
          new FinancialPositionResult.Reported(
              BookkeepingReadStatementPublishedLanguageTranslator.toPublished(reported.value()));
      case BookkeepingReadOutcome.Rejected<
                  dev.erst.fingrind.executor.bookkeeping.FinancialPositionView>
              rejected ->
          new FinancialPositionResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Computes one income statement for the selected book and reporting period. */
  public IncomeStatementResult incomeStatement(IncomeStatementQuery query) {
    return switch (bookkeepingReadService.incomeStatement(
        BookReadQueryTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<
                  dev.erst.fingrind.executor.bookkeeping.IncomeStatementView>
              reported ->
          new IncomeStatementResult.Reported(
              BookkeepingReadStatementPublishedLanguageTranslator.toPublished(reported.value()));
      case BookkeepingReadOutcome.Rejected<
                  dev.erst.fingrind.executor.bookkeeping.IncomeStatementView>
              rejected ->
          new IncomeStatementResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Computes one statement of changes in equity for the selected book and reporting period. */
  public ChangesInEquityResult changesInEquity(ChangesInEquityQuery query) {
    return switch (bookkeepingReadService.changesInEquity(
        BookReadQueryTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<
                  dev.erst.fingrind.executor.bookkeeping.ChangesInEquityView>
              reported ->
          new ChangesInEquityResult.Reported(
              BookkeepingReadStatementPublishedLanguageTranslator.toPublished(reported.value()));
      case BookkeepingReadOutcome.Rejected<
                  dev.erst.fingrind.executor.bookkeeping.ChangesInEquityView>
              rejected ->
          new ChangesInEquityResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  private static BookQueryRejection toPublished(
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

  private BookIdentity currentBookIdentity() {
    return requireInitializedBookIdentity(bookkeepingReadService.inspectBook());
  }

  private BookInspection.ResultTransferReadiness resultTransferReadiness(
      BookIdentity bookIdentity) {
    var requiredClassification =
        bookkeepingReadService.requiredResultHoldingClassification(bookIdentity);
    ResultHoldingSelection selection = bookkeepingReadService.resultHoldingSelection(bookIdentity);
    return switch (selection) {
      case AcceptedResultHoldingSelection accepted ->
          new BookInspection.ResultTransferReadiness(
              true,
              requiredClassification,
              accepted.account().accountCode(),
              null,
              null,
              List.of());
      case RejectedResultHoldingSelection rejected -> {
        BookAdministrationRejection published =
            BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection());
        yield new BookInspection.ResultTransferReadiness(
            false,
            requiredClassification,
            null,
            BookAdministrationRejection.wireCode(published),
            RejectionNarrative.message(published),
            rejected.candidateAccountCodes());
      }
    };
  }

  static BookIdentity requireInitializedBookIdentity(
      dev.erst.fingrind.executor.spi.BookLifecycleInspection inspection) {
    Objects.requireNonNull(inspection, "inspection");
    return switch (inspection) {
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Initialized initialized ->
          initialized.bookIdentity();
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Missing _ ->
          throw new IllegalStateException(
              "Book identity is unavailable because the book is missing.");
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Existing existing ->
          throw new IllegalStateException(
              "Book identity is unavailable for non-initialized book status "
                  + existing.status().wireValue()
                  + ".");
    };
  }
}
