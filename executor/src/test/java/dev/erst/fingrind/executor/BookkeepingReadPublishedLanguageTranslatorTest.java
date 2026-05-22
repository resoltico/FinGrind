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
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountPage;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.postingPage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
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
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceRowView;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.time.LocalDate;
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
        accountPage(
            List.of(CASH_ACCOUNT),
            25,
            Optional.of(new AccountPageCursor(REVENUE_ACCOUNT.accountCode()))),
        BookkeepingReadPublishedLanguageTranslator.toPublished(
            bookIdentity(),
            new AccountRegistryPage(
                List.of(REGISTERED_CASH_ACCOUNT),
                25,
                Optional.of(new AccountRegistryCursor(REVENUE_ACCOUNT.accountCode())))));
    assertEquals(
        postingPage(
            Optional.of(CASH_ACCOUNT.accountCode()),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            List.of(BookkeepingPublishedLanguageTranslator.toPublished(postingFact)),
            20,
            Optional.of(
                new PostingPageCursor(EFFECTIVE_DATE, FIXED_INSTANT, new PostingId("posting-1")))),
        BookkeepingReadPublishedLanguageTranslator.toPublished(
            bookIdentity(),
            new PostingHistoryQuery(
                Optional.of(CASH_ACCOUNT.accountCode()),
                EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
                20,
                Optional.of(PostingHistoryCursor.fromPosting(postingFact))),
            new PostingHistoryPage(
                List.of(postingFact),
                20,
                Optional.of(PostingHistoryCursor.fromPosting(postingFact)))));
  }

  @Test
  void toPublishedTrialBalance_aggregatesCurrencyTotalsAndBalancedFlags() {
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
        BookkeepingReadPublishedLanguageTranslator.toPublished(trialBalanceView));
  }

  @Test
  void localReadQueries_liftSharedKernelDateRangesAndValidatePagingContracts() {
    assertEquals(
        new AccountBalanceCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS),
        AccountBalanceCriteria.unbounded(
            CASH_ACCOUNT.accountCode(), PostingCoverage.NON_CLOSING_POSTINGS));
    assertEquals(
        new AccountBalanceCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS),
        AccountBalanceCriteria.unbounded(CASH_ACCOUNT.accountCode()));
    assertEquals(
        new AccountBalanceCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.ALL_POSTING_KINDS),
        new AccountBalanceCriteria(CASH_ACCOUNT.accountCode(), EFFECTIVE_DATE, EFFECTIVE_DATE));
    assertEquals(
        new AccountBalanceCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.NON_CLOSING_POSTINGS),
        new AccountBalanceCriteria(
            CASH_ACCOUNT.accountCode(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            PostingCoverage.NON_CLOSING_POSTINGS));
    assertEquals(
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS),
        AccountLedgerCriteria.unbounded(
            CASH_ACCOUNT.accountCode(), PostingCoverage.NON_CLOSING_POSTINGS));
    assertEquals(
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS),
        AccountLedgerCriteria.unbounded(CASH_ACCOUNT.accountCode()));
    assertEquals(
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.ALL_POSTING_KINDS),
        new AccountLedgerCriteria(CASH_ACCOUNT.accountCode(), EFFECTIVE_DATE, EFFECTIVE_DATE));
    assertEquals(
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.NON_CLOSING_POSTINGS),
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            PostingCoverage.NON_CLOSING_POSTINGS));
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
  void
      accountLedgerPublication_synthesizesExplicitZeroOpeningAndClosingBalancesForBoundedEmptyView() {
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
        BookkeepingReadPublishedLanguageTranslator.toPublished(bookIdentity(), emptyBoundedLedger)
            .openingBalances());
    assertEquals(
        List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
        BookkeepingReadPublishedLanguageTranslator.toPublished(bookIdentity(), emptyBoundedLedger)
            .closingBalances());
  }

  @Test
  void accountLedgerPublication_preservesUnboundedOpeningAbsenceForMinimumLowerBound() {
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
        BookkeepingReadPublishedLanguageTranslator.toPublished(bookIdentity(), minimumBoundLedger)
            .openingBalances());
    assertEquals(
        List.of(),
        BookkeepingReadPublishedLanguageTranslator.toPublished(bookIdentity(), minimumBoundLedger)
            .closingBalances());
  }

  @Test
  void accountLedgerPublication_preservesUnboundedOpeningAbsenceForOpenLowerBound() {
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
        BookkeepingReadPublishedLanguageTranslator.toPublished(bookIdentity(), unboundedLedger)
            .openingBalances());
    assertEquals(
        List.of(),
        BookkeepingReadPublishedLanguageTranslator.toPublished(bookIdentity(), unboundedLedger)
            .closingBalances());
  }

  @Test
  void accountLedgerPublication_preservesExplicitOpeningAndClosingBalances() {
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
        BookkeepingReadPublishedLanguageTranslator.toPublished(bookIdentity(), populatedLedger)
            .openingBalances());
    assertEquals(
        List.of(currencyBalance("13.00", "0.00", "13.00", BalanceSide.DEBIT)),
        BookkeepingReadPublishedLanguageTranslator.toPublished(bookIdentity(), populatedLedger)
            .closingBalances());
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
            bookIdentity(),
            Optional.of(EFFECTIVE_DATE),
            EffectiveDateRange.of(null, EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new FinancialPositionSectionView(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRowView(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT)))),
            List.of(
                new FinancialPositionSectionView(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRowView(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT)))));
    IncomeStatementView incomeStatementView =
        new IncomeStatementView(
            bookIdentity(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            EffectiveDateRange.of(EFFECTIVE_DATE.minusYears(1), EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                new IncomeStatementSectionView(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRowView(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            Optional.of(AccountRole.ORDINARY),
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(
                new IncomeStatementSectionView(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRowView(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            Optional.of(AccountRole.ORDINARY),
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)));
    ChangesInEquityView changesInEquityView =
        new ChangesInEquityView(
            bookIdentity(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            EffectiveDateRange.of(EFFECTIVE_DATE.minusYears(1), EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new ChangesInEquityRowView(
                    "current-period-result",
                    "Current Period Result",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(
                new ChangesInEquityRowView(
                    "current-period-result",
                    "Current Period Result",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
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
            bookIdentity(),
            Optional.of(EFFECTIVE_DATE),
            EffectiveDateRange.of(null, EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRow(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT)))),
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRow(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))))),
        BookkeepingReadPublishedLanguageTranslator.toPublished(financialPositionView));
    assertEquals(
        new IncomeStatementReport(
            bookIdentity(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            EffectiveDateRange.of(EFFECTIVE_DATE.minusYears(1), EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRow(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            Optional.of(AccountRole.ORDINARY),
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRow(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            Optional.of(AccountRole.ORDINARY),
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
        BookkeepingReadPublishedLanguageTranslator.toPublished(incomeStatementView));
    assertEquals(
        new ChangesInEquityReport(
            bookIdentity(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            EffectiveDateRange.of(EFFECTIVE_DATE.minusYears(1), EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new ChangesInEquityRow(
                    "current-period-result",
                    "Current Period Result",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(
                new ChangesInEquityRow(
                    "current-period-result",
                    "Current Period Result",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
        BookkeepingReadPublishedLanguageTranslator.toPublished(changesInEquityView));
  }
}
