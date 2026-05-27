package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
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

/** Projects bookkeeping read reports into the public bookkeeping contract. */
public final class BookkeepingReadReportPublishedLanguageTranslator {
  private BookkeepingReadReportPublishedLanguageTranslator() {}

  /** Projects one local trial-balance view back into the public published language. */
  public static TrialBalanceReport toPublished(TrialBalanceView view) {
    Objects.requireNonNull(view, "view");
    List<TrialBalanceRow> rows =
        view.rows().stream()
            .map(BookkeepingReadReportPublishedLanguageTranslator::toPublished)
            .toList();
    List<TrialBalanceRow> comparativeRows =
        view.comparativeRows().stream()
            .map(BookkeepingReadReportPublishedLanguageTranslator::toPublished)
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
            .map(BookkeepingReadReportPublishedLanguageTranslator::toPublished)
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
            .map(BookkeepingReadReportPublishedLanguageTranslator::toPublished)
            .toList(),
        view.accountActivity().stream()
            .map(BookkeepingReadReportPublishedLanguageTranslator::toPublished)
            .toList());
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

  private static CurrencyBalance zeroLedgerBalance(BookIdentity bookIdentity) {
    return BalanceMath.currencyBalance(bookIdentity.functionalCurrency(), 0L, 0L);
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
