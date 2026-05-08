package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.AccountPageCursor;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceRow;
import java.util.Objects;

/** Translates public read/report DTOs at the bookkeeping boundary. */
public final class BookkeepingReadPublishedLanguageTranslator {
  private BookkeepingReadPublishedLanguageTranslator() {}

  /** Translates one public account-registry query into the local bookkeeping read model. */
  public static AccountRegistryQuery fromPublished(ListAccountsQuery query) {
    Objects.requireNonNull(query, "query");
    return new AccountRegistryQuery(
        query.limit(),
        query.cursor().map(cursor -> new AccountRegistryCursor(cursor.accountCode())));
  }

  /** Translates one public posting-history query into the local bookkeeping read model. */
  public static PostingHistoryQuery fromPublished(ListPostingsQuery query) {
    Objects.requireNonNull(query, "query");
    return new PostingHistoryQuery(
        query.accountCode(),
        query.effectiveDateRange(),
        query.limit(),
        query.cursor().map(BookkeepingReadPublishedLanguageTranslator::fromPublished));
  }

  /** Translates one public account-balance query into the local bookkeeping read model. */
  public static AccountBalanceCriteria fromPublished(AccountBalanceQuery query) {
    Objects.requireNonNull(query, "query");
    return new AccountBalanceCriteria(query.accountCode(), query.effectiveDateRange());
  }

  /** Translates one public trial-balance query into the local bookkeeping read model. */
  public static TrialBalanceCriteria fromPublished(TrialBalanceQuery query) {
    Objects.requireNonNull(query, "query");
    return new TrialBalanceCriteria(query.effectiveDateTo());
  }

  /** Translates one public account-ledger query into the local bookkeeping read model. */
  public static AccountLedgerCriteria fromPublished(AccountLedgerQuery query) {
    Objects.requireNonNull(query, "query");
    return new AccountLedgerCriteria(query.accountCode(), query.effectiveDateRange());
  }

  /** Translates one public period-summary query into the local bookkeeping read model. */
  public static PeriodSummaryCriteria fromPublished(PeriodSummaryQuery query) {
    Objects.requireNonNull(query, "query");
    return new PeriodSummaryCriteria(query.effectiveDateFrom(), query.effectiveDateTo());
  }

  /** Projects one local account-registry page back into the public published language. */
  public static AccountPage toPublished(AccountRegistryPage page) {
    Objects.requireNonNull(page, "page");
    return new AccountPage(
        page.accounts().stream().map(BookkeepingPublishedLanguageTranslator::toPublished).toList(),
        page.limit(),
        page.nextCursor().map(BookkeepingReadPublishedLanguageTranslator::toPublished));
  }

  /** Projects one local posting-history page back into the public published language. */
  public static PostingPage toPublished(PostingHistoryPage page) {
    Objects.requireNonNull(page, "page");
    return new PostingPage(
        page.postings().stream().map(BookkeepingPublishedLanguageTranslator::toPublished).toList(),
        page.limit(),
        page.nextCursor().map(BookkeepingReadPublishedLanguageTranslator::toPublished));
  }

  /** Projects one local account-balance view back into the public published language. */
  public static AccountBalanceSnapshot toPublished(AccountBalanceView view) {
    Objects.requireNonNull(view, "view");
    return new AccountBalanceSnapshot(
        BookkeepingPublishedLanguageTranslator.toPublished(view.account()),
        view.effectiveDateRange().effectiveDateFrom(),
        view.effectiveDateRange().effectiveDateTo(),
        view.balances());
  }

  /** Projects one local trial-balance view back into the public published language. */
  public static TrialBalanceReport toPublished(TrialBalanceView view) {
    Objects.requireNonNull(view, "view");
    return new TrialBalanceReport(
        view.effectiveDateTo(),
        view.rows().stream().map(BookkeepingReadPublishedLanguageTranslator::toPublished).toList());
  }

  /** Projects one local account-ledger view back into the public published language. */
  public static AccountLedgerReport toPublished(AccountLedgerView view) {
    Objects.requireNonNull(view, "view");
    return new AccountLedgerReport(
        BookkeepingPublishedLanguageTranslator.toPublished(view.account()),
        view.effectiveDateRange(),
        view.openingBalances(),
        view.entries().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList(),
        view.closingBalances());
  }

  /** Projects one local period-summary view back into the public published language. */
  public static PeriodSummaryReport toPublished(PeriodSummaryView view) {
    Objects.requireNonNull(view, "view");
    return new PeriodSummaryReport(
        view.effectiveDateFrom(),
        view.effectiveDateTo(),
        view.postingCount(),
        view.postingLineCount(),
        view.accountsTouched(),
        view.currencySummaries().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList(),
        view.accountActivity().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList());
  }

  /** Projects one local bookkeeping query rejection into the public contract. */
  public static BookQueryRejection toPublished(BookkeepingQueryRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return switch (rejection) {
      case BookkeepingQueryRejection.BookNotInitialized _ ->
          new BookQueryRejection.BookNotInitialized();
      case BookkeepingQueryRejection.UnknownAccount unknownAccount ->
          new BookQueryRejection.UnknownAccount(unknownAccount.accountCode());
      case BookkeepingQueryRejection.PostingNotFound postingNotFound ->
          new BookQueryRejection.PostingNotFound(postingNotFound.postingId());
    };
  }

  private static PostingHistoryCursor fromPublished(PostingPageCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return new PostingHistoryCursor(
        cursor.effectiveDate(), cursor.recordedAt(), cursor.postingId());
  }

  private static AccountPageCursor toPublished(AccountRegistryCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return new AccountPageCursor(cursor.accountCode());
  }

  private static PostingPageCursor toPublished(PostingHistoryCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return new PostingPageCursor(cursor.effectiveDate(), cursor.recordedAt(), cursor.postingId());
  }

  private static TrialBalanceRow toPublished(TrialBalanceRowView row) {
    Objects.requireNonNull(row, "row");
    return new TrialBalanceRow(
        BookkeepingPublishedLanguageTranslator.toPublished(row.account()), row.balance());
  }

  private static AccountLedgerEntry toPublished(AccountLedgerEntryView entry) {
    Objects.requireNonNull(entry, "entry");
    return new AccountLedgerEntry(
        BookkeepingPublishedLanguageTranslator.toPublished(entry.posting()),
        entry.movement(),
        entry.runningNetAmount(),
        entry.runningBalanceSide());
  }

  private static PeriodCurrencySummary toPublished(PeriodCurrencySummaryView row) {
    Objects.requireNonNull(row, "row");
    return new PeriodCurrencySummary(row.totals());
  }

  private static PeriodAccountActivityRow toPublished(PeriodAccountActivityView row) {
    Objects.requireNonNull(row, "row");
    return new PeriodAccountActivityRow(
        BookkeepingPublishedLanguageTranslator.toPublished(row.account()), row.movement());
  }
}
