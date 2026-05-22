package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    return new AccountBalanceCriteria(
        query.accountCode(), query.effectiveDateRange(), query.postingCoverage());
  }

  /** Translates one public trial-balance query into the local bookkeeping read model. */
  public static TrialBalanceCriteria fromPublished(TrialBalanceQuery query) {
    Objects.requireNonNull(query, "query");
    return new TrialBalanceCriteria(query.effectiveDateAsOf(), query.postingCoverage());
  }

  /** Translates one public account-ledger query into the local bookkeeping read model. */
  public static AccountLedgerCriteria fromPublished(AccountLedgerQuery query) {
    Objects.requireNonNull(query, "query");
    return new AccountLedgerCriteria(
        query.accountCode(), query.effectiveDateRange(), query.postingCoverage());
  }

  /** Translates one public period-summary query into the local bookkeeping read model. */
  public static PeriodSummaryCriteria fromPublished(PeriodSummaryQuery query) {
    Objects.requireNonNull(query, "query");
    return new PeriodSummaryCriteria(
        query.effectiveDateFrom(), query.effectiveDateTo(), query.postingCoverage());
  }

  /** Translates one public financial-position query into the local bookkeeping read model. */
  public static FinancialPositionCriteria fromPublished(FinancialPositionQuery query) {
    Objects.requireNonNull(query, "query");
    return new FinancialPositionCriteria(query.effectiveDateAsOf());
  }

  /** Translates one public income-statement query into the local bookkeeping read model. */
  public static IncomeStatementCriteria fromPublished(IncomeStatementQuery query) {
    Objects.requireNonNull(query, "query");
    return new IncomeStatementCriteria(query.effectiveDateFrom(), query.effectiveDateTo());
  }

  /** Translates one public changes-in-equity query into the local bookkeeping read model. */
  public static ChangesInEquityCriteria fromPublished(ChangesInEquityQuery query) {
    Objects.requireNonNull(query, "query");
    return new ChangesInEquityCriteria(query.effectiveDateFrom(), query.effectiveDateTo());
  }

  /** Projects one local account-registry page back into the public published language. */
  public static AccountPage toPublished(BookIdentity bookIdentity, AccountRegistryPage page) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(page, "page");
    return new AccountPage(
        bookIdentity,
        page.accounts().stream().map(BookkeepingPublishedLanguageTranslator::toPublished).toList(),
        page.limit(),
        page.nextCursor().map(BookkeepingReadPublishedLanguageTranslator::toPublished));
  }

  /** Projects one local posting-history page back into the public published language. */
  public static PostingPage toPublished(
      BookIdentity bookIdentity, PostingHistoryQuery query, PostingHistoryPage page) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(page, "page");
    return new PostingPage(
        bookIdentity,
        query.accountCode(),
        query.effectiveDateRange(),
        page.postings().stream().map(BookkeepingPublishedLanguageTranslator::toPublished).toList(),
        page.limit(),
        page.nextCursor().map(BookkeepingReadPublishedLanguageTranslator::toPublished));
  }

  /** Projects one local account-balance view back into the public published language. */
  public static AccountBalanceSnapshot toPublished(
      BookIdentity bookIdentity, AccountBalanceView view) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(view, "view");
    return new AccountBalanceSnapshot(
        bookIdentity,
        BookkeepingPublishedLanguageTranslator.toPublished(view.account()),
        view.effectiveDateRange().effectiveDateFrom(),
        view.effectiveDateRange().effectiveDateTo(),
        view.postingCoverage(),
        view.balances());
  }

  /** Projects one local trial-balance view back into the public published language. */
  public static TrialBalanceReport toPublished(TrialBalanceView view) {
    Objects.requireNonNull(view, "view");
    List<TrialBalanceRow> rows =
        view.rows().stream().map(BookkeepingReadPublishedLanguageTranslator::toPublished).toList();
    List<TrialBalanceRow> comparativeRows =
        view.comparativeRows().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList();
    List<CurrencyBalance> totals = aggregateTrialBalanceTotals(rows);
    List<CurrencyBalance> comparativeTotals = aggregateTrialBalanceTotals(comparativeRows);
    return new TrialBalanceReport(
        view.bookIdentity(),
        view.effectiveDateAsOf(),
        view.comparativeEffectiveDateRange(),
        view.postingCoverage(),
        rows,
        totals,
        isBalanced(totals),
        comparativeRows,
        comparativeTotals,
        isBalanced(comparativeTotals));
  }

  /** Projects one local account-ledger view back into the public published language. */
  public static AccountLedgerReport toPublished(BookIdentity bookIdentity, AccountLedgerView view) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(view, "view");
    return new AccountLedgerReport(
        bookIdentity,
        BookkeepingPublishedLanguageTranslator.toPublished(view.account()),
        view.effectiveDateRange(),
        view.postingCoverage(),
        normalizedOpeningLedgerBalances(bookIdentity, view),
        view.entries().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList(),
        normalizedClosingLedgerBalances(bookIdentity, view));
  }

  /** Projects one local period-summary view back into the public published language. */
  public static PeriodSummaryReport toPublished(BookIdentity bookIdentity, PeriodSummaryView view) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(view, "view");
    return new PeriodSummaryReport(
        bookIdentity,
        view.effectiveDateFrom(),
        view.effectiveDateTo(),
        view.postingCoverage(),
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

  /** Projects one local statement of financial position back into the public published language. */
  public static FinancialPositionReport toPublished(FinancialPositionView view) {
    Objects.requireNonNull(view, "view");
    return new FinancialPositionReport(
        view.bookIdentity(),
        view.effectiveDateAsOf(),
        view.comparativeEffectiveDateRange(),
        view.postingCoverage(),
        view.sections().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList(),
        view.comparativeSections().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList());
  }

  /** Projects one local income statement back into the public published language. */
  public static IncomeStatementReport toPublished(IncomeStatementView view) {
    Objects.requireNonNull(view, "view");
    return new IncomeStatementReport(
        view.bookIdentity(),
        view.effectiveDateFrom(),
        view.effectiveDateTo(),
        view.comparativeEffectiveDateRange(),
        view.postingCoverage(),
        view.sections().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList(),
        view.netIncomeTotals(),
        view.comparativeSections().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList(),
        view.comparativeNetIncomeTotals());
  }

  /** Projects one local statement of changes in equity back into the public language. */
  public static ChangesInEquityReport toPublished(ChangesInEquityView view) {
    Objects.requireNonNull(view, "view");
    return new ChangesInEquityReport(
        view.bookIdentity(),
        view.effectiveDateFrom(),
        view.effectiveDateTo(),
        view.comparativeEffectiveDateRange(),
        view.postingCoverage(),
        view.rows().stream().map(BookkeepingReadPublishedLanguageTranslator::toPublished).toList(),
        view.openingTotals(),
        view.movementTotals(),
        view.closingTotals(),
        view.comparativeRows().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList(),
        view.comparativeOpeningTotals(),
        view.comparativeMovementTotals(),
        view.comparativeClosingTotals());
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

  private static List<CurrencyBalance> normalizedOpeningLedgerBalances(
      BookIdentity bookIdentity, AccountLedgerView view) {
    if (!view.openingBalances().isEmpty()) {
      return view.openingBalances();
    }
    LocalDate lowerBound = view.effectiveDateRange().effectiveDateFrom().orElse(null);
    if (lowerBound == null || lowerBound.equals(LocalDate.MIN)) {
      return List.of();
    }
    return List.of(zeroLedgerBalance(bookIdentity));
  }

  private static List<CurrencyBalance> normalizedClosingLedgerBalances(
      BookIdentity bookIdentity, AccountLedgerView view) {
    if (!view.closingBalances().isEmpty()) {
      return view.closingBalances();
    }
    if (view.effectiveDateRange().effectiveDateTo().isEmpty()) {
      return List.of();
    }
    return List.of(zeroLedgerBalance(bookIdentity));
  }

  private static CurrencyBalance zeroLedgerBalance(BookIdentity bookIdentity) {
    return BalanceMath.currencyBalance(bookIdentity.functionalCurrency(), 0L, 0L);
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

  private static FinancialPositionSection toPublished(FinancialPositionSectionView section) {
    Objects.requireNonNull(section, "section");
    return new FinancialPositionSection(
        section.accountType(),
        section.rows().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList(),
        section.totals());
  }

  private static FinancialPositionRow toPublished(FinancialPositionRowView row) {
    Objects.requireNonNull(row, "row");
    return new FinancialPositionRow(
        row.lineCode(),
        row.lineName(),
        row.lineType(),
        row.lineRole(),
        row.lineClassification(),
        row.lineKind(),
        row.balance());
  }

  private static IncomeStatementSection toPublished(IncomeStatementSectionView section) {
    Objects.requireNonNull(section, "section");
    return new IncomeStatementSection(
        section.accountType(),
        section.rows().stream()
            .map(BookkeepingReadPublishedLanguageTranslator::toPublished)
            .toList(),
        section.totals());
  }

  private static IncomeStatementRow toPublished(IncomeStatementRowView row) {
    Objects.requireNonNull(row, "row");
    return new IncomeStatementRow(
        row.lineCode(),
        row.lineName(),
        row.lineType(),
        row.lineRole(),
        row.lineClassification(),
        row.lineKind(),
        row.movement());
  }

  private static ChangesInEquityRow toPublished(ChangesInEquityRowView row) {
    Objects.requireNonNull(row, "row");
    return new ChangesInEquityRow(
        row.lineCode(),
        row.lineName(),
        row.lineType(),
        row.lineRole(),
        row.lineClassification(),
        row.lineKind(),
        row.openingBalance(),
        row.movement(),
        row.closingBalance());
  }

  private static List<CurrencyBalance> aggregateTrialBalanceTotals(List<TrialBalanceRow> rows) {
    List<CurrencyBalance> totalsByCurrency = new ArrayList<>();
    for (TrialBalanceRow row : rows) {
      mergeCurrencyBalance(totalsByCurrency, row.balance());
    }
    return List.copyOf(totalsByCurrency);
  }

  private static boolean isBalanced(List<CurrencyBalance> totals) {
    return totals.stream().allMatch(total -> total.balanceSide() == BalanceSide.ZERO);
  }

  private static CurrencyBalance sumCurrencyBalances(CurrencyBalance left, CurrencyBalance right) {
    return BalanceMath.currencyBalance(
        left.debitTotal().currencyUnit(),
        Math.addExact(left.debitTotal().minorUnits(), right.debitTotal().minorUnits()),
        Math.addExact(left.creditTotal().minorUnits(), right.creditTotal().minorUnits()));
  }

  private static void mergeCurrencyBalance(
      List<CurrencyBalance> totalsByCurrency, CurrencyBalance candidate) {
    CurrencyUnit currencyUnit = candidate.debitTotal().currencyUnit();
    for (int index = 0; index < totalsByCurrency.size(); index++) {
      CurrencyBalance existing = totalsByCurrency.get(index);
      if (existing.debitTotal().currencyUnit().equals(currencyUnit)) {
        totalsByCurrency.set(index, sumCurrencyBalances(existing, candidate));
        return;
      }
    }
    totalsByCurrency.add(candidate);
  }
}
