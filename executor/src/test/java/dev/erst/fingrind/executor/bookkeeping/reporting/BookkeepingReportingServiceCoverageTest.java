package dev.erst.fingrind.executor.bookkeeping.reporting;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CashFlowStatementView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityCriteria;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityRowView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityView;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionCriteria;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused coverage tests for private reporting-service branches and ordering seams. */
class BookkeepingReportingServiceCoverageTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-05-13T11:00:00Z");
  private static final LocalDate PERIOD_FROM = LocalDate.parse("2026-04-07");
  private static final LocalDate PERIOD_TO = LocalDate.parse("2026-04-08");

  @Test
  void comparators_breakTiesByCurrencyCode() {
    CurrencyBalance eurDebit = balance("EUR", "1.00", "0.00");
    CurrencyBalance usdDebit = balance("USD", "1.00", "0.00");
    FinancialPositionRowView eurPositionRow =
        financialPositionRowView("1000", "Cash", AccountType.ASSET, eurDebit);
    FinancialPositionRowView usdPositionRow =
        financialPositionRowView("1000", "Cash", AccountType.ASSET, usdDebit);
    IncomeStatementRowView eurIncomeRow =
        incomeStatementRowView("4000", "Sales", AccountType.REVENUE, eurDebit);
    IncomeStatementRowView usdIncomeRow =
        incomeStatementRowView("4000", "Sales", AccountType.REVENUE, usdDebit);
    ChangesInEquityRowView eurEquityRow =
        equityRowView("3000", "Capital", eurDebit, eurDebit, eurDebit);
    ChangesInEquityRowView usdEquityRow =
        equityRowView("3000", "Capital", usdDebit, usdDebit, usdDebit);

    assertTrue(BookkeepingReportingService.BALANCE_ORDER.compare(eurDebit, usdDebit) < 0);
    assertTrue(
        BookkeepingReportingService.FINANCIAL_POSITION_ROW_ORDER.compare(
                eurPositionRow, usdPositionRow)
            < 0);
    assertTrue(
        BookkeepingReportingService.INCOME_STATEMENT_ROW_ORDER.compare(eurIncomeRow, usdIncomeRow)
            < 0);
    assertTrue(
        BookkeepingReportingService.CHANGES_IN_EQUITY_ROW_ORDER.compare(eurEquityRow, usdEquityRow)
            < 0);
  }

  @Test
  void changesInEquity_usesOpeningAndMovementFallbacksAndSkipsNonEquityRows() {
    RegisteredAccount assetAccount =
        account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT);
    RegisteredAccount openingEquityAccount =
        account("3000", "Owner Capital", AccountType.EQUITY, NormalBalance.CREDIT);
    RegisteredAccount movementEquityAccount =
        account("3010", "Reserve", AccountType.EQUITY, NormalBalance.CREDIT);
    CoverageBookStore store =
        new CoverageBookStore(
            initializedInspection(),
            Map.of(
                queryKey(
                    EffectiveDateRange.of(null, PERIOD_FROM.minusDays(1)),
                    PostingCoverage.ALL_POSTING_KINDS),
                List.of(
                    totals(assetAccount, "EUR", 1000L, 0L),
                    totals(openingEquityAccount, "EUR", 0L, 1000L)),
                queryKey(
                    EffectiveDateRange.of(PERIOD_FROM, PERIOD_TO),
                    PostingCoverage.ALL_POSTING_KINDS),
                List.of(totals(movementEquityAccount, "EUR", 0L, 250L)),
                queryKey(EffectiveDateRange.of(null, PERIOD_TO), PostingCoverage.ALL_POSTING_KINDS),
                List.of()));

    ChangesInEquityView view =
        new BookkeepingReportingService(store)
            .changesInEquity(
                new ChangesInEquityCriteria(PERIOD_FROM, PERIOD_TO, ComparativeSelection.none()));

    assertEquals(
        List.of("3000", "3010"),
        view.rows().stream().map(ChangesInEquityRowView::lineCode).toList());
    assertFalse(view.rows().stream().anyMatch(row -> "1000".equals(row.lineCode())));
    assertEquals(balance("EUR", "0.00", "10.00"), view.rows().getFirst().openingBalance());
    assertEquals(balance("EUR", "0.00", "0.00"), view.rows().getFirst().movement());
    assertEquals(balance("EUR", "0.00", "0.00"), view.rows().getFirst().closingBalance());
    assertEquals(balance("EUR", "0.00", "0.00"), view.rows().get(1).openingBalance());
    assertEquals(balance("EUR", "0.00", "2.50"), view.rows().get(1).movement());
  }

  @Test
  void financialPosition_requiresOneInitializedBookForMissingAndExistingSnapshots() {
    BookkeepingReportingService missingService =
        new BookkeepingReportingService(
            new CoverageBookStore(new BookLifecycleInspection.Missing(2), Map.of()));
    BookkeepingReportingService existingService =
        new BookkeepingReportingService(
            new CoverageBookStore(
                new BookLifecycleInspection.Existing(
                    BookLifecycleInspection.Status.BLANK_SQLITE, 0, 0, 2),
                Map.of()));

    IllegalStateException missingFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                missingService.financialPosition(
                    new FinancialPositionCriteria(Optional.empty(), ComparativeSelection.none())));
    IllegalStateException existingFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                existingService.financialPosition(
                    new FinancialPositionCriteria(Optional.empty(), ComparativeSelection.none())));

    assertEquals(
        "Statement computation requires one initialized book.", missingFailure.getMessage());
    assertEquals(
        "Statement computation requires one initialized book.", existingFailure.getMessage());
  }

  @Test
  void financialPosition_withoutAsOfDate_omitsComparativeSections() {
    RegisteredAccount cash = account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT);
    RegisteredAccount capital =
        account("3000", "Capital", AccountType.EQUITY, NormalBalance.CREDIT);
    CoverageBookStore store =
        new CoverageBookStore(
            initializedInspection(),
            Map.of(
                queryKey(EffectiveDateRange.of(null, null), PostingCoverage.ALL_POSTING_KINDS),
                List.of(totals(cash, "EUR", 1000L, 0L), totals(capital, "EUR", 0L, 1000L))));

    FinancialPositionView view =
        new BookkeepingReportingService(store)
            .financialPosition(
                new FinancialPositionCriteria(Optional.empty(), ComparativeSelection.none()));

    assertEquals(EffectiveDateRange.of(null, null), view.comparativeEffectiveDateRange());
    assertTrue(view.comparativeSections().isEmpty());
  }

  @Test
  void privateAssertions_reportMissingSectionsAndTreatZeroBalancesAsSignedZero() {
    IllegalStateException missingSectionFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                BookkeepingReportingService.assertAccountingEquation(
                    List.of(
                        new FinancialPositionSectionView(
                            AccountType.ASSET,
                            List.of(),
                            List.of(balance("EUR", "1.00", "0.00"))))));

    assertEquals(
        "Missing statement-of-financial-position section: LIABILITY",
        missingSectionFailure.getMessage());
    assertEquals(
        0L,
        BookkeepingReportingService.signedMinorUnits(
            BalanceMath.currencyBalance(CurrencyUnit.of("EUR"), 0L, 0L)));
  }

  @Test
  void assertAccountingEquation_acceptsBalancedSectionTotals() {
    BookkeepingReportingService.assertAccountingEquation(
        List.of(
            new FinancialPositionSectionView(
                AccountType.ASSET, List.of(), List.of(balance("EUR", "10.00", "0.00"))),
            new FinancialPositionSectionView(
                AccountType.LIABILITY, List.of(), List.of(balance("EUR", "0.00", "4.00"))),
            new FinancialPositionSectionView(
                AccountType.EQUITY, List.of(), List.of(balance("EUR", "0.00", "6.00")))));
  }

  @Test
  void comparativeWindows_followFiscalYearAnchorInsteadOfBlindCalendarSubtraction() {
    BookIdentity fiscalYearShiftedIdentity =
        new BookIdentity(
            new EntityProfile(new BookEntityName("Shifted Year Shop")),
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("02-29"));
    CoverageBookStore store =
        new CoverageBookStore(
            new BookLifecycleInspection.Initialized(
                1001, 2, 2, FIXED_INSTANT, fiscalYearShiftedIdentity),
            Map.of());
    BookkeepingReportingService service = new BookkeepingReportingService(store);

    FinancialPositionCriteria financialPositionCriteria =
        new FinancialPositionCriteria(
            Optional.of(LocalDate.parse("2025-02-28")), ComparativeSelection.priorPeriod());
    IncomeStatementCriteria incomeStatementCriteria =
        new IncomeStatementCriteria(
            LocalDate.parse("2025-02-28"),
            LocalDate.parse("2025-03-01"),
            ComparativeSelection.priorPeriod());

    assertEquals(
        EffectiveDateRange.of(null, LocalDate.parse("2024-02-29")),
        service.financialPosition(financialPositionCriteria).comparativeEffectiveDateRange());
    assertEquals(
        EffectiveDateRange.of(LocalDate.parse("2024-02-29"), LocalDate.parse("2024-03-01")),
        service.incomeStatement(incomeStatementCriteria).comparativeEffectiveDateRange());
    assertEquals(
        EffectiveDateRange.of(LocalDate.parse("2024-02-29"), LocalDate.parse("2024-03-01")),
        service
            .changesInEquity(
                new ChangesInEquityCriteria(
                    incomeStatementCriteria.effectiveDateFrom(),
                    incomeStatementCriteria.effectiveDateTo(),
                    ComparativeSelection.priorPeriod()))
            .comparativeEffectiveDateRange());
  }

  @Test
  void statementComparatives_accept_explicit_ranges_for_period_reports() {
    RegisteredAccount revenueAccount =
        account("4000", "Sales", AccountType.REVENUE, NormalBalance.CREDIT);
    RegisteredAccount expenseAccount =
        account("5000", "Operations", AccountType.EXPENSE, NormalBalance.DEBIT);
    RegisteredAccount equityAccount =
        account("3000", "Owner Capital", AccountType.EQUITY, NormalBalance.CREDIT);
    EffectiveDateRange comparativeRange =
        EffectiveDateRange.of(LocalDate.parse("2025-04-07"), LocalDate.parse("2025-04-08"));
    CoverageBookStore store =
        new CoverageBookStore(
            initializedInspection(),
            Map.of(
                queryKey(
                    EffectiveDateRange.of(PERIOD_FROM, PERIOD_TO),
                    PostingCoverage.NON_CLOSING_POSTINGS),
                List.of(
                    totals(revenueAccount, "EUR", 0L, 200L),
                    totals(expenseAccount, "EUR", 50L, 0L)),
                queryKey(comparativeRange, PostingCoverage.NON_CLOSING_POSTINGS),
                List.of(
                    totals(revenueAccount, "EUR", 0L, 100L),
                    totals(expenseAccount, "EUR", 25L, 0L)),
                queryKey(
                    EffectiveDateRange.of(null, PERIOD_FROM.minusDays(1)),
                    PostingCoverage.ALL_POSTING_KINDS),
                List.of(totals(equityAccount, "EUR", 0L, 500L)),
                queryKey(
                    EffectiveDateRange.of(PERIOD_FROM, PERIOD_TO),
                    PostingCoverage.ALL_POSTING_KINDS),
                List.of(totals(equityAccount, "EUR", 0L, 25L)),
                queryKey(EffectiveDateRange.of(null, PERIOD_TO), PostingCoverage.ALL_POSTING_KINDS),
                List.of(totals(equityAccount, "EUR", 0L, 525L)),
                queryKey(
                    EffectiveDateRange.of(
                        null, comparativeRange.effectiveDateFrom().orElseThrow().minusDays(1)),
                    PostingCoverage.ALL_POSTING_KINDS),
                List.of(totals(equityAccount, "EUR", 0L, 400L)),
                queryKey(comparativeRange, PostingCoverage.ALL_POSTING_KINDS),
                List.of(totals(equityAccount, "EUR", 0L, 10L)),
                queryKey(
                    EffectiveDateRange.of(null, comparativeRange.effectiveDateTo().orElseThrow()),
                    PostingCoverage.ALL_POSTING_KINDS),
                List.of(totals(equityAccount, "EUR", 0L, 410L))));
    BookkeepingReportingService service = new BookkeepingReportingService(store);

    IncomeStatementView incomeStatement =
        service.incomeStatement(
            new IncomeStatementCriteria(
                PERIOD_FROM, PERIOD_TO, ComparativeSelection.range(comparativeRange)));
    ChangesInEquityView changesInEquity =
        service.changesInEquity(
            new ChangesInEquityCriteria(
                PERIOD_FROM, PERIOD_TO, ComparativeSelection.range(comparativeRange)));

    assertEquals(comparativeRange, incomeStatement.comparativeEffectiveDateRange());
    assertFalse(incomeStatement.comparativeSections().isEmpty());
    assertFalse(incomeStatement.comparativeNetIncomeTotals().isEmpty());
    assertEquals(comparativeRange, changesInEquity.comparativeEffectiveDateRange());
    assertFalse(changesInEquity.comparativeRows().isEmpty());
    assertFalse(changesInEquity.comparativeClosingTotals().isEmpty());
  }

  @Test
  void cashFlowStatement_requiresOpeningPlusMovementToEqualClosingCash() {
    RegisteredAccount cashAccount = account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT);
    CoverageBookStore store =
        new CoverageBookStore(
            initializedInspection(),
            Map.of(
                queryKey(
                    EffectiveDateRange.to(PERIOD_FROM.minusDays(1)),
                    PostingCoverage.NON_CLOSING_POSTINGS),
                List.of(totals(cashAccount, "EUR", 1000L, 0L)),
                queryKey(EffectiveDateRange.to(PERIOD_TO), PostingCoverage.NON_CLOSING_POSTINGS),
                List.of(totals(cashAccount, "EUR", 1200L, 0L))));

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                new BookkeepingReportingService(store)
                    .cashFlowStatement(
                        new dev.erst.fingrind.executor.bookkeeping.CashFlowStatementCriteria(
                            PERIOD_FROM, PERIOD_TO, ComparativeSelection.none())));

    assertEquals(
        "Cash-flow articulation failed for currency EUR: opening cash plus movement does not equal closing cash.",
        failure.getMessage());
  }

  @Test
  void cashFlowStatement_supportsPriorPeriodComparatives_and_normalizesCreditAndZeroCashBalances() {
    RegisteredAccount cashAccount = account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT);
    RegisteredAccount pettyCashAccount =
        account("1010", "Petty Cash", AccountType.ASSET, NormalBalance.DEBIT);
    EffectiveDateRange comparativeRange =
        EffectiveDateRange.of(LocalDate.parse("2025-04-07"), LocalDate.parse("2025-04-08"));
    CoverageBookStore store =
        new CoverageBookStore(
            initializedInspection(),
            List.of(cashAccount, cashAccount, pettyCashAccount),
            List.of(),
            Map.of(
                queryKey(
                    EffectiveDateRange.of(null, PERIOD_FROM.minusDays(1)),
                    PostingCoverage.NON_CLOSING_POSTINGS),
                List.of(
                    totals(cashAccount, "EUR", 0L, 100L), totals(pettyCashAccount, "EUR", 0L, 0L)),
                queryKey(
                    EffectiveDateRange.of(PERIOD_FROM, PERIOD_TO),
                    PostingCoverage.NON_CLOSING_POSTINGS),
                List.of(),
                queryKey(
                    EffectiveDateRange.of(null, PERIOD_TO), PostingCoverage.NON_CLOSING_POSTINGS),
                List.of(
                    totals(cashAccount, "EUR", 0L, 100L), totals(pettyCashAccount, "EUR", 0L, 0L)),
                queryKey(
                    EffectiveDateRange.of(
                        null, comparativeRange.effectiveDateFrom().orElseThrow().minusDays(1)),
                    PostingCoverage.NON_CLOSING_POSTINGS),
                List.of(
                    totals(cashAccount, "EUR", 0L, 50L), totals(pettyCashAccount, "EUR", 0L, 0L)),
                queryKey(comparativeRange, PostingCoverage.NON_CLOSING_POSTINGS),
                List.of(),
                queryKey(
                    EffectiveDateRange.of(null, comparativeRange.effectiveDateTo().orElseThrow()),
                    PostingCoverage.NON_CLOSING_POSTINGS),
                List.of(
                    totals(cashAccount, "EUR", 0L, 50L), totals(pettyCashAccount, "EUR", 0L, 0L))));

    CashFlowStatementView view =
        new BookkeepingReportingService(store)
            .cashFlowStatement(
                new dev.erst.fingrind.executor.bookkeeping.CashFlowStatementCriteria(
                    PERIOD_FROM, PERIOD_TO, ComparativeSelection.priorPeriod()));

    assertEquals(comparativeRange, view.comparativeEffectiveDateRange());
    assertEquals(List.of(balance("EUR", "0.00", "1.00")), view.openingCashTotals());
    assertEquals(List.of(balance("EUR", "0.00", "1.00")), view.closingCashTotals());
    assertTrue(view.sections().stream().allMatch(section -> section.rows().isEmpty()));
    assertTrue(view.movementTotals().isEmpty());
    assertEquals(List.of(balance("EUR", "0.00", "0.50")), view.comparativeOpeningCashTotals());
    assertEquals(List.of(balance("EUR", "0.00", "0.50")), view.comparativeClosingCashTotals());
  }

  private static RegisteredAccount account(
      String code, String name, AccountType accountType, NormalBalance normalBalance) {
    return new RegisteredAccount(
        new AccountCode(code),
        new AccountName(name),
        accountType,
        accountTaxonomy(accountType, normalBalance),
        true,
        FIXED_INSTANT);
  }

  private static FinancialPositionRowView financialPositionRowView(
      String lineCode, String lineName, AccountType lineType, CurrencyBalance balance) {
    return new FinancialPositionRowView(
        lineCode,
        lineName,
        lineType,
        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
        StatementLineKind.DECLARED_ACCOUNT,
        balance);
  }

  private static IncomeStatementRowView incomeStatementRowView(
      String lineCode, String lineName, AccountType lineType, CurrencyBalance movement) {
    return new IncomeStatementRowView(
        lineCode,
        lineName,
        lineType,
        ProfitAndLossLineClassification.OPERATING_REVENUE,
        StatementLineKind.DECLARED_ACCOUNT,
        movement);
  }

  private static ChangesInEquityRowView equityRowView(
      String lineCode,
      String lineName,
      CurrencyBalance openingBalance,
      CurrencyBalance movement,
      CurrencyBalance closingBalance) {
    return new ChangesInEquityRowView(
        lineCode,
        lineName,
        Optional.of(AccountType.EQUITY),
        Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
        StatementLineKind.DECLARED_ACCOUNT,
        openingBalance,
        movement,
        closingBalance);
  }

  private static AccountCurrencyTotals totals(
      RegisteredAccount account, String currencyCode, long debitMinor, long creditMinor) {
    return new AccountCurrencyTotals(
        account, CurrencyUnit.of(currencyCode), debitMinor, creditMinor);
  }

  private static CurrencyBalance balance(
      String currencyCode, String debitAmount, String creditAmount) {
    return CurrencyBalance.ofTotals(
        Money.parse(currencyCode, debitAmount), Money.parse(currencyCode, creditAmount));
  }

  private static BookLifecycleInspection.Initialized initializedInspection() {
    return new BookLifecycleInspection.Initialized(1001, 2, 2, FIXED_INSTANT, bookIdentity());
  }

  private static QueryKey queryKey(EffectiveDateRange range, PostingCoverage postingCoverage) {
    return new QueryKey(range, postingCoverage);
  }

  private record QueryKey(EffectiveDateRange range, PostingCoverage postingCoverage) {}

  /** Minimal statement-store double for targeted statement-service coverage. */
  private static final class CoverageBookStore implements BookkeepingReadStore {
    private final BookLifecycleInspection inspection;
    private final List<RegisteredAccount> accounts;
    private final List<CommittedPosting> postings;
    private final Map<QueryKey, List<AccountCurrencyTotals>> totalsByQuery;

    private CoverageBookStore(
        BookLifecycleInspection inspection,
        Map<QueryKey, List<AccountCurrencyTotals>> totalsByQuery) {
      this(
          inspection,
          totalsByQuery.values().stream()
              .flatMap(List::stream)
              .map(AccountCurrencyTotals::account)
              .distinct()
              .toList(),
          List.of(),
          totalsByQuery);
    }

    private CoverageBookStore(
        BookLifecycleInspection inspection,
        List<RegisteredAccount> accounts,
        List<CommittedPosting> postings,
        Map<QueryKey, List<AccountCurrencyTotals>> totalsByQuery) {
      this.inspection = inspection;
      this.accounts = List.copyOf(accounts);
      this.postings = List.copyOf(postings);
      this.totalsByQuery = totalsByQuery;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return inspection;
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return allAccounts().stream()
          .filter(account -> account.accountCode().equals(accountCode))
          .findFirst();
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(
        java.util.Set<AccountCode> accountCodes) {
      return allAccounts().stream()
          .filter(account -> accountCodes.contains(account.accountCode()))
          .collect(
              java.util.stream.Collectors.toMap(
                  RegisteredAccount::accountCode, account -> account));
    }

    @Override
    public List<dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord>
        inventoryValuationMovements(Optional<LocalDate> effectiveDateAsOf) {
      return List.of();
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(
        dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(dev.erst.fingrind.core.PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(
        dev.erst.fingrind.core.PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      return accounts;
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return postings;
    }

    @Override
    public PostingHistoryPage listPostings(PostingHistoryQuery query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<AccountCurrencyTotals> accountTotals(
        EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
      return totalsByQuery.getOrDefault(queryKey(effectiveDateRange, postingCoverage), List.of());
    }

    @Override
    public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<java.time.LocalDate> latestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.TrialBalanceView trialBalance(
        TrialBalanceCriteria query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
      throw new UnsupportedOperationException();
    }
  }
}
