package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.BookStore;
import java.util.Objects;

/** Application service that owns every read-only book workflow behind one unified seam. */
public final class BookReadService {
  private final BookkeepingReadService bookkeepingReadService;

  /** Creates the read service with its application-owned inspection, query, and report seam. */
  public BookReadService(BookStore bookStore) {
    this.bookkeepingReadService =
        new BookkeepingReadService(Objects.requireNonNull(bookStore, "bookStore"));
  }

  /** Inspects the selected book file without mutating it. */
  public BookInspection inspectBook() {
    return BookInspectionPublishedLanguageTranslator.toPublished(
        bookkeepingReadService.inspectBook());
  }

  /** Lists one paginated slice of the current account registry for the selected book. */
  public ListAccountsResult listAccounts(ListAccountsQuery query) {
    return switch (bookkeepingReadService.listAccounts(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<AccountRegistryPage> reported ->
          new ListAccountsResult.Listed(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookkeepingReadOutcome.Rejected<AccountRegistryPage> rejected ->
          new ListAccountsResult.Rejected(
              BookkeepingReadPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  /** Returns one committed posting by durable posting identity. */
  public GetPostingResult getPosting(PostingId postingId) {
    BookkeepingReadOutcome<CommittedPosting> outcome = bookkeepingReadService.getPosting(postingId);
    if (outcome instanceof BookkeepingReadOutcome.Reported<CommittedPosting> reported) {
      return new GetPostingResult.Found(
          BookkeepingPublishedLanguageTranslator.toPublished(reported.value()));
    }
    BookkeepingReadOutcome.Rejected<CommittedPosting> rejected =
        (BookkeepingReadOutcome.Rejected<CommittedPosting>) outcome;
    return new GetPostingResult.Rejected(
        BookkeepingReadPublishedLanguageTranslator.toPublished(rejected.rejection()));
  }

  /** Returns one filtered page of committed postings. */
  public ListPostingsResult listPostings(ListPostingsQuery query) {
    return switch (bookkeepingReadService.listPostings(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<PostingHistoryPage> reported ->
          new ListPostingsResult.Listed(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookkeepingReadOutcome.Rejected<PostingHistoryPage> rejected ->
          new ListPostingsResult.Rejected(
              BookkeepingReadPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  /** Computes one grouped per-currency balance snapshot for the selected declared account. */
  public AccountBalanceResult accountBalance(AccountBalanceQuery query) {
    return switch (bookkeepingReadService.accountBalance(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<
                  dev.erst.fingrind.executor.bookkeeping.AccountBalanceView>
              reported ->
          new AccountBalanceResult.Reported(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookkeepingReadOutcome.Rejected<
                  dev.erst.fingrind.executor.bookkeeping.AccountBalanceView>
              rejected ->
          new AccountBalanceResult.Rejected(
              BookkeepingReadPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  /** Computes one book-wide trial balance. */
  public TrialBalanceResult trialBalance(TrialBalanceQuery query) {
    return switch (bookkeepingReadService.trialBalance(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<dev.erst.fingrind.executor.bookkeeping.TrialBalanceView>
              reported ->
          new TrialBalanceResult.Reported(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookkeepingReadOutcome.Rejected<dev.erst.fingrind.executor.bookkeeping.TrialBalanceView>
              rejected ->
          new TrialBalanceResult.Rejected(
              BookkeepingReadPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  /** Computes one running ledger for the selected declared account. */
  public AccountLedgerResult accountLedger(AccountLedgerQuery query) {
    return switch (bookkeepingReadService.accountLedger(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<dev.erst.fingrind.executor.bookkeeping.AccountLedgerView>
              reported ->
          new AccountLedgerResult.Reported(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookkeepingReadOutcome.Rejected<dev.erst.fingrind.executor.bookkeeping.AccountLedgerView>
              rejected ->
          new AccountLedgerResult.Rejected(
              BookkeepingReadPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  /** Computes one bounded period summary for the selected book. */
  public PeriodSummaryResult periodSummary(PeriodSummaryQuery query) {
    return switch (bookkeepingReadService.periodSummary(
        BookkeepingReadPublishedLanguageTranslator.fromPublished(query))) {
      case BookkeepingReadOutcome.Reported<dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView>
              reported ->
          new PeriodSummaryResult.Reported(
              BookkeepingReadPublishedLanguageTranslator.toPublished(reported.value()));
      case BookkeepingReadOutcome.Rejected<dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView>
              rejected ->
          new PeriodSummaryResult.Rejected(
              BookkeepingReadPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }
}
