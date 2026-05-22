package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EFFECTIVE_DATE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_CREDIT_BALANCE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_DEBIT_BALANCE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_NET_ZERO;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.REVENUE_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.currencyBalance;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.declareDefaultAccounts;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.postingFact;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.readService;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.allPostingKinds;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests covering report-oriented queries in {@link BookReadService}. */
class BookReadServiceReportQueryTest {
  @Test
  void trialBalance_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = readService(bookSession);

      assertEquals(
          new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.trialBalance(
              new TrialBalanceQuery(Optional.of(EFFECTIVE_DATE), allPostingKinds())));
    }
  }

  @Test
  void trialBalance_reportsExpectedSnapshot() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(postingFact("posting-1", "idem-1"));
      BookReadService service = readService(bookSession);

      assertEquals(
          new TrialBalanceResult.Reported(
              trialBalanceReport(
                  bookIdentity(),
                  Optional.of(EFFECTIVE_DATE),
                  EffectiveDateRange.of(null, EFFECTIVE_DATE.minusYears(1)),
                  allPostingKinds(),
                  List.of(
                      new TrialBalanceRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE),
                      new TrialBalanceRow(REVENUE_ACCOUNT, EUR_CREDIT_BALANCE)),
                  List.of())),
          service.trialBalance(
              new TrialBalanceQuery(Optional.of(EFFECTIVE_DATE), allPostingKinds())));
    }
  }

  @Test
  void trialBalance_withoutAsOfDate_omitsComparativeRows() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(postingFact("posting-1", "idem-1"));
      BookReadService service = readService(bookSession);

      assertEquals(
          new TrialBalanceResult.Reported(
              trialBalanceReport(
                  bookIdentity(),
                  Optional.empty(),
                  EffectiveDateRange.of(null, null),
                  allPostingKinds(),
                  List.of(
                      new TrialBalanceRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE),
                      new TrialBalanceRow(REVENUE_ACCOUNT, EUR_CREDIT_BALANCE)),
                  List.of())),
          service.trialBalance(new TrialBalanceQuery(Optional.empty(), allPostingKinds())));
    }
  }

  @Test
  void accountLedger_rejectsUninitializedAndUnknownAccount() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = readService(uninitializedBook);

      assertEquals(
          new AccountLedgerResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.accountLedger(
              new AccountLedgerQuery(
                  CASH_ACCOUNT.accountCode(), EffectiveDateRange.unbounded(), allPostingKinds())));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = readService(bookSession);

      assertEquals(
          new AccountLedgerResult.Rejected(
              new BookQueryRejection.UnknownAccount(CASH_ACCOUNT.accountCode())),
          service.accountLedger(
              new AccountLedgerQuery(
                  CASH_ACCOUNT.accountCode(), EffectiveDateRange.unbounded(), allPostingKinds())));
    }
  }

  @Test
  void accountLedger_reportsOpeningEntriesAndClosingBalances() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      var postingFact = postingFact("posting-1", "idem-1");
      var publishedPostingFact = BookkeepingPublishedLanguageTranslator.toPublished(postingFact);
      bookSession.commit(postingFact);
      BookReadService service = readService(bookSession);

      assertEquals(
          new AccountLedgerResult.Reported(
              new AccountLedgerReport(
                  bookIdentity(),
                  CASH_ACCOUNT,
                  EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
                  allPostingKinds(),
                  List.of(currencyBalance("0", "0", "0", BalanceSide.ZERO)),
                  List.of(
                      new AccountLedgerEntry(
                          publishedPostingFact,
                          EUR_DEBIT_BALANCE,
                          Money.parse("EUR", "10.00"),
                          BalanceSide.DEBIT)),
                  List.of(EUR_DEBIT_BALANCE))),
          service.accountLedger(
              new AccountLedgerQuery(CASH_ACCOUNT.accountCode(), EFFECTIVE_DATE, EFFECTIVE_DATE)));
    }
  }

  @Test
  void currentBookIdentity_rejectsMissingLifecycleIdentity() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                BookReadService.requireInitializedBookIdentity(
                    new BookLifecycleInspection.Missing(2)));

    assertEquals("Book identity is unavailable because the book is missing.", failure.getMessage());
  }

  @Test
  void periodSummary_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = readService(bookSession);

      assertEquals(
          new PeriodSummaryResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.periodSummary(new PeriodSummaryQuery(EFFECTIVE_DATE, EFFECTIVE_DATE)));
    }
  }

  @Test
  void periodSummary_reportsCurrencyAndAccountActivity() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(postingFact("posting-1", "idem-1"));
      BookReadService service = readService(bookSession);

      assertEquals(
          new PeriodSummaryResult.Reported(
              new PeriodSummaryReport(
                  bookIdentity(),
                  EFFECTIVE_DATE,
                  EFFECTIVE_DATE,
                  allPostingKinds(),
                  1,
                  2,
                  2,
                  List.of(new PeriodCurrencySummary(EUR_NET_ZERO)),
                  List.of(
                      new PeriodAccountActivityRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE),
                      new PeriodAccountActivityRow(REVENUE_ACCOUNT, EUR_CREDIT_BALANCE)))),
          service.periodSummary(new PeriodSummaryQuery(EFFECTIVE_DATE, EFFECTIVE_DATE)));
    }
  }

  @Test
  void currentBookIdentity_rejectsNonInitializedLifecycleIdentity() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                BookReadService.requireInitializedBookIdentity(
                    new BookLifecycleInspection.Existing(
                        BookLifecycleInspection.Status.BLANK_SQLITE, 0, 0, 2)));

    assertEquals(
        "Book identity is unavailable for non-initialized book status blank-sqlite.",
        failure.getMessage());
  }

  private static TrialBalanceReport trialBalanceReport(
      dev.erst.fingrind.core.BookIdentity bookIdentity,
      Optional<java.time.LocalDate> effectiveDateAsOf,
      EffectiveDateRange comparativeEffectiveDateRange,
      dev.erst.fingrind.core.PostingCoverage postingCoverage,
      List<TrialBalanceRow> rows,
      List<TrialBalanceRow> comparativeRows) {
    List<CurrencyBalance> totals = trialBalanceTotals(rows);
    List<CurrencyBalance> comparativeTotals = trialBalanceTotals(comparativeRows);
    return new TrialBalanceReport(
        bookIdentity,
        effectiveDateAsOf,
        comparativeEffectiveDateRange,
        postingCoverage,
        rows,
        totals,
        isBalanced(totals),
        comparativeRows,
        comparativeTotals,
        isBalanced(comparativeTotals));
  }

  private static List<CurrencyBalance> trialBalanceTotals(List<TrialBalanceRow> rows) {
    List<CurrencyBalance> totalsByCurrency = new ArrayList<>();
    for (TrialBalanceRow row : rows) {
      mergeCurrencyBalance(totalsByCurrency, row.balance());
    }
    return List.copyOf(totalsByCurrency);
  }

  private static CurrencyBalance sumCurrencyBalances(CurrencyBalance left, CurrencyBalance right) {
    return CurrencyBalance.ofTotals(
        left.debitTotal().plus(right.debitTotal()), left.creditTotal().plus(right.creditTotal()));
  }

  private static boolean isBalanced(List<CurrencyBalance> totals) {
    return totals.stream().allMatch(balance -> balance.balanceSide() == BalanceSide.ZERO);
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
