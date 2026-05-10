package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EFFECTIVE_DATE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_CREDIT_BALANCE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_DEBIT_BALANCE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_NET_ZERO;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.REVENUE_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.declareDefaultAccounts;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.postingFact;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.readService;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
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
          service.trialBalance(new TrialBalanceQuery(Optional.of(EFFECTIVE_DATE))));
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
              new TrialBalanceReport(
                  Optional.of(EFFECTIVE_DATE),
                  List.of(
                      new TrialBalanceRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE),
                      new TrialBalanceRow(REVENUE_ACCOUNT, EUR_CREDIT_BALANCE)))),
          service.trialBalance(new TrialBalanceQuery(Optional.of(EFFECTIVE_DATE))));
    }
  }

  @Test
  void accountLedger_rejectsUninitializedAndUnknownAccount() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = readService(uninitializedBook);

      assertEquals(
          new AccountLedgerResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.accountLedger(
              new AccountLedgerQuery(CASH_ACCOUNT.accountCode(), EffectiveDateRange.unbounded())));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = readService(bookSession);

      assertEquals(
          new AccountLedgerResult.Rejected(
              new BookQueryRejection.UnknownAccount(CASH_ACCOUNT.accountCode())),
          service.accountLedger(
              new AccountLedgerQuery(CASH_ACCOUNT.accountCode(), EffectiveDateRange.unbounded())));
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
                  CASH_ACCOUNT,
                  EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
                  List.of(),
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
                  EFFECTIVE_DATE,
                  EFFECTIVE_DATE,
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
}
