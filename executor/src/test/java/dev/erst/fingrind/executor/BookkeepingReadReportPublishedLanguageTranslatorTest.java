package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EFFECTIVE_DATE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_CREDIT_BALANCE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_DEBIT_BALANCE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_NET_ZERO;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.REGISTERED_CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.REGISTERED_REVENUE_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.REVENUE_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.currencyBalance;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.postingFact;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadReportPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.PeriodAccountActivityView;
import dev.erst.fingrind.executor.bookkeeping.PeriodCurrencySummaryView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceRowView;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers report publication from local bookkeeping views into the public contract. */
class BookkeepingReadReportPublishedLanguageTranslatorTest {
  @Test
  void reportTranslator_aggregatesTrialBalanceCurrencyTotalsAndBalancedFlags() {
    CurrencyBalance usdDebitBalance =
        CurrencyBalance.ofTotals(Money.parse("USD", "5.00"), Money.parse("USD", "0"));
    TrialBalanceView trialBalanceView =
        new TrialBalanceView(
            bookIdentity(),
            Optional.of(EFFECTIVE_DATE),
            EffectiveDateRange.of(EFFECTIVE_DATE.minusYears(1), EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new TrialBalanceRowView(REGISTERED_CASH_ACCOUNT, EUR_DEBIT_BALANCE),
                new TrialBalanceRowView(REGISTERED_REVENUE_ACCOUNT, EUR_CREDIT_BALANCE),
                new TrialBalanceRowView(REGISTERED_CASH_ACCOUNT, usdDebitBalance)),
            List.of(new TrialBalanceRowView(REGISTERED_CASH_ACCOUNT, EUR_DEBIT_BALANCE)));

    assertEquals(
        new TrialBalanceReport(
            bookIdentity(),
            Optional.of(EFFECTIVE_DATE),
            EffectiveDateRange.of(EFFECTIVE_DATE.minusYears(1), EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new TrialBalanceRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE),
                new TrialBalanceRow(REVENUE_ACCOUNT, EUR_CREDIT_BALANCE),
                new TrialBalanceRow(CASH_ACCOUNT, usdDebitBalance)),
            List.of(EUR_NET_ZERO, usdDebitBalance),
            false,
            List.of(new TrialBalanceRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE)),
            List.of(EUR_DEBIT_BALANCE),
            false),
        BookkeepingReadReportPublishedLanguageTranslator.toPublished(trialBalanceView));
  }

  @Test
  void reportTranslator_synthesizesExplicitZeroOpeningAndClosingBalancesForBoundedEmptyLedger() {
    AccountLedgerView emptyBoundedLedger =
        new AccountLedgerView(
            REGISTERED_CASH_ACCOUNT,
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(),
            List.of(),
            List.of());

    assertEquals(
        List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
        BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                bookIdentity(), emptyBoundedLedger)
            .openingBalances());
    assertEquals(
        List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
        BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                bookIdentity(), emptyBoundedLedger)
            .closingBalances());
  }

  @Test
  void reportTranslator_preservesUnboundedOpeningAbsenceForMinimumLowerBound() {
    AccountLedgerView minimumBoundLedger =
        new AccountLedgerView(
            REGISTERED_CASH_ACCOUNT,
            EffectiveDateRange.of(LocalDate.MIN, null),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(),
            List.of(),
            List.of());

    assertEquals(
        List.of(),
        BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                bookIdentity(), minimumBoundLedger)
            .openingBalances());
    assertEquals(
        List.of(),
        BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                bookIdentity(), minimumBoundLedger)
            .closingBalances());
  }

  @Test
  void reportTranslator_preservesUnboundedOpeningAbsenceForOpenLowerBound() {
    AccountLedgerView unboundedLedger =
        new AccountLedgerView(
            REGISTERED_CASH_ACCOUNT,
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(),
            List.of(),
            List.of());

    assertEquals(
        List.of(),
        BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                bookIdentity(), unboundedLedger)
            .openingBalances());
    assertEquals(
        List.of(),
        BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                bookIdentity(), unboundedLedger)
            .closingBalances());
  }

  @Test
  void reportTranslator_preservesExplicitOpeningAndClosingBalances() {
    AccountLedgerView populatedLedger =
        new AccountLedgerView(
            REGISTERED_CASH_ACCOUNT,
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(currencyBalance("3.00", "0.00", "3.00", BalanceSide.DEBIT)),
            List.of(),
            List.of(currencyBalance("13.00", "0.00", "13.00", BalanceSide.DEBIT)));

    assertEquals(
        List.of(currencyBalance("3.00", "0.00", "3.00", BalanceSide.DEBIT)),
        BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                bookIdentity(), populatedLedger)
            .openingBalances());
    assertEquals(
        List.of(currencyBalance("13.00", "0.00", "13.00", BalanceSide.DEBIT)),
        BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                bookIdentity(), populatedLedger)
            .closingBalances());
  }

  @Test
  void reportModels_validateAndCopyPeriodSummaryAndPostingCursorState() {
    var postingFact = postingFact("posting-1", "idem-1");
    List<PeriodCurrencySummaryView> currencySummaries =
        new ArrayList<>(List.of(new PeriodCurrencySummaryView(EUR_NET_ZERO)));
    List<PeriodAccountActivityView> accountActivity =
        new ArrayList<>(
            List.of(new PeriodAccountActivityView(REGISTERED_CASH_ACCOUNT, EUR_DEBIT_BALANCE)));
    PeriodSummaryView view =
        new PeriodSummaryView(
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            PostingCoverage.ALL_POSTING_KINDS,
            1,
            2,
            1,
            currencySummaries,
            accountActivity);

    currencySummaries.clear();
    accountActivity.clear();

    assertEquals(
        new PostingHistoryCursor(EFFECTIVE_DATE, FIXED_INSTANT, new PostingId("posting-1")),
        PostingHistoryCursor.fromPosting(postingFact));
    assertEquals(1, view.currencySummaries().size());
    assertEquals(1, view.accountActivity().size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryView(
                EFFECTIVE_DATE.plusDays(1),
                EFFECTIVE_DATE,
                PostingCoverage.ALL_POSTING_KINDS,
                1,
                2,
                1,
                List.of(new PeriodCurrencySummaryView(EUR_NET_ZERO)),
                List.of(
                    new PeriodAccountActivityView(REGISTERED_CASH_ACCOUNT, EUR_DEBIT_BALANCE))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryView(
                EFFECTIVE_DATE,
                EFFECTIVE_DATE,
                PostingCoverage.ALL_POSTING_KINDS,
                -1,
                2,
                1,
                List.of(new PeriodCurrencySummaryView(EUR_NET_ZERO)),
                List.of(
                    new PeriodAccountActivityView(REGISTERED_CASH_ACCOUNT, EUR_DEBIT_BALANCE))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryView(
                EFFECTIVE_DATE,
                EFFECTIVE_DATE,
                PostingCoverage.ALL_POSTING_KINDS,
                1,
                -1,
                1,
                List.of(new PeriodCurrencySummaryView(EUR_NET_ZERO)),
                List.of(
                    new PeriodAccountActivityView(REGISTERED_CASH_ACCOUNT, EUR_DEBIT_BALANCE))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryView(
                EFFECTIVE_DATE,
                EFFECTIVE_DATE,
                PostingCoverage.ALL_POSTING_KINDS,
                1,
                2,
                -1,
                List.of(new PeriodCurrencySummaryView(EUR_NET_ZERO)),
                List.of(
                    new PeriodAccountActivityView(REGISTERED_CASH_ACCOUNT, EUR_DEBIT_BALANCE))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PeriodSummaryCriteria(EFFECTIVE_DATE.plusDays(1), EFFECTIVE_DATE));
  }
}
