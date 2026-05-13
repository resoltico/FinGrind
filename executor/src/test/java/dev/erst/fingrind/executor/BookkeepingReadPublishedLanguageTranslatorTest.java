package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EFFECTIVE_DATE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_DEBIT_BALANCE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_NET_ZERO;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.REGISTERED_CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.REVENUE_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.currencyBalance;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.postingFact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
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
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityCriteria;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityRowView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionCriteria;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementSectionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementView;
import dev.erst.fingrind.executor.bookkeeping.PeriodAccountActivityView;
import dev.erst.fingrind.executor.bookkeeping.PeriodCurrencySummaryView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for the local bookkeeping read-model translator and helper types. */
class BookkeepingReadPublishedLanguageTranslatorTest {
  @Test
  void readTranslator_roundTripsPublishedQueriesAndPagesWithPaginationCursors() {
    ListAccountsQuery accountsQuery =
        new ListAccountsQuery(25, Optional.of(new AccountPageCursor(CASH_ACCOUNT.accountCode())));
    ListPostingsQuery postingsQuery =
        new ListPostingsQuery(
            Optional.of(CASH_ACCOUNT.accountCode()),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            20,
            Optional.of(
                new PostingPageCursor(EFFECTIVE_DATE, FIXED_INSTANT, new PostingId("posting-1"))));
    var postingFact = postingFact("posting-1", "idem-1");

    assertEquals(
        new AccountRegistryQuery(
            25, Optional.of(new AccountRegistryCursor(CASH_ACCOUNT.accountCode()))),
        BookkeepingReadPublishedLanguageTranslator.fromPublished(accountsQuery));
    assertEquals(
        new PostingHistoryQuery(
            Optional.of(CASH_ACCOUNT.accountCode()),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            20,
            Optional.of(
                new PostingHistoryCursor(
                    EFFECTIVE_DATE, FIXED_INSTANT, new PostingId("posting-1")))),
        BookkeepingReadPublishedLanguageTranslator.fromPublished(postingsQuery));
    assertEquals(
        new AccountPage(
            List.of(CASH_ACCOUNT),
            25,
            Optional.of(new AccountPageCursor(REVENUE_ACCOUNT.accountCode()))),
        BookkeepingReadPublishedLanguageTranslator.toPublished(
            new AccountRegistryPage(
                List.of(REGISTERED_CASH_ACCOUNT),
                25,
                Optional.of(new AccountRegistryCursor(REVENUE_ACCOUNT.accountCode())))));
    assertEquals(
        new PostingPage(
            List.of(BookkeepingPublishedLanguageTranslator.toPublished(postingFact)),
            20,
            Optional.of(
                new PostingPageCursor(EFFECTIVE_DATE, FIXED_INSTANT, new PostingId("posting-1")))),
        BookkeepingReadPublishedLanguageTranslator.toPublished(
            new PostingHistoryPage(
                List.of(postingFact),
                20,
                Optional.of(PostingHistoryCursor.fromPosting(postingFact)))));
  }

  @Test
  void localReadQueries_liftSharedKernelDateRangesAndValidatePagingContracts() {
    assertEquals(
        new AccountBalanceCriteria(
            CASH_ACCOUNT.accountCode(), EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE)),
        new AccountBalanceCriteria(CASH_ACCOUNT.accountCode(), EFFECTIVE_DATE, EFFECTIVE_DATE));
    assertEquals(
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(), EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE)),
        new AccountLedgerCriteria(CASH_ACCOUNT.accountCode(), EFFECTIVE_DATE, EFFECTIVE_DATE));
    assertEquals(
        new PostingHistoryQuery(
            Optional.of(CASH_ACCOUNT.accountCode()),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            20,
            Optional.empty()),
        new PostingHistoryQuery(
            Optional.of(CASH_ACCOUNT.accountCode()),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            20,
            Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AccountRegistryQuery(InteractionLimits.PAGE_LIMIT_MIN - 1, Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AccountRegistryQuery(InteractionLimits.PAGE_LIMIT_MAX + 1, Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostingHistoryQuery(
                Optional.empty(),
                EffectiveDateRange.unbounded(),
                InteractionLimits.PAGE_LIMIT_MAX + 1,
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostingHistoryQuery(
                Optional.empty(),
                EffectiveDateRange.unbounded(),
                InteractionLimits.PAGE_LIMIT_MIN - 1,
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PeriodSummaryCriteria(EFFECTIVE_DATE.plusDays(1), EFFECTIVE_DATE));
  }

  @Test
  void localPeriodSummaryAndPostingCursorModels_validateAndCopyTheirState() {
    var postingFact = postingFact("posting-1", "idem-1");
    List<PeriodCurrencySummaryView> currencySummaries =
        new ArrayList<>(List.of(new PeriodCurrencySummaryView(EUR_NET_ZERO)));
    List<PeriodAccountActivityView> accountActivity =
        new ArrayList<>(
            List.of(new PeriodAccountActivityView(REGISTERED_CASH_ACCOUNT, EUR_DEBIT_BALANCE)));
    PeriodSummaryView view =
        new PeriodSummaryView(
            EFFECTIVE_DATE, EFFECTIVE_DATE, 1, 2, 1, currencySummaries, accountActivity);

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
                1,
                2,
                -1,
                List.of(new PeriodCurrencySummaryView(EUR_NET_ZERO)),
                List.of(
                    new PeriodAccountActivityView(
                        REGISTERED_CASH_ACCOUNT,
                        currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT)))));
  }

  @Test
  void readTranslator_roundTripsStatementQueriesAndReports() {
    FinancialPositionQuery financialPositionQuery =
        new FinancialPositionQuery(Optional.of(EFFECTIVE_DATE));
    IncomeStatementQuery incomeStatementQuery =
        new IncomeStatementQuery(EFFECTIVE_DATE, EFFECTIVE_DATE);
    ChangesInEquityQuery changesInEquityQuery =
        new ChangesInEquityQuery(EFFECTIVE_DATE, EFFECTIVE_DATE);
    FinancialPositionView financialPositionView =
        new FinancialPositionView(
            Optional.of(EFFECTIVE_DATE),
            List.of(
                new FinancialPositionSectionView(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRowView(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            false,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT)))));
    IncomeStatementView incomeStatementView =
        new IncomeStatementView(
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            List.of(
                new IncomeStatementSectionView(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRowView(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            false,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)));
    ChangesInEquityView changesInEquityView =
        new ChangesInEquityView(
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            List.of(
                new ChangesInEquityRowView(
                    "current-earnings",
                    "Current Earnings",
                    true,
                    currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)));

    assertEquals(
        new FinancialPositionCriteria(Optional.of(EFFECTIVE_DATE)),
        BookkeepingReadPublishedLanguageTranslator.fromPublished(financialPositionQuery));
    assertEquals(
        new IncomeStatementCriteria(EFFECTIVE_DATE, EFFECTIVE_DATE),
        BookkeepingReadPublishedLanguageTranslator.fromPublished(incomeStatementQuery));
    assertEquals(
        new ChangesInEquityCriteria(EFFECTIVE_DATE, EFFECTIVE_DATE),
        BookkeepingReadPublishedLanguageTranslator.fromPublished(changesInEquityQuery));
    assertEquals(
        new FinancialPositionReport(
            Optional.of(EFFECTIVE_DATE),
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRow(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            false,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))))),
        BookkeepingReadPublishedLanguageTranslator.toPublished(financialPositionView));
    assertEquals(
        new IncomeStatementReport(
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRow(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            false,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
        BookkeepingReadPublishedLanguageTranslator.toPublished(incomeStatementView));
    assertEquals(
        new ChangesInEquityReport(
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            List.of(
                new ChangesInEquityRow(
                    "current-earnings",
                    "Current Earnings",
                    true,
                    currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
        BookkeepingReadPublishedLanguageTranslator.toPublished(changesInEquityView));
  }
}
