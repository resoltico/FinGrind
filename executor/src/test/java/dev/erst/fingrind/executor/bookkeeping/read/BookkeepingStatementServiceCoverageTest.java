package dev.erst.fingrind.executor.bookkeeping.read;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReportingObligationStatus;
import dev.erst.fingrind.core.StatementLineKind;
import dev.erst.fingrind.core.TaxProfile;
import dev.erst.fingrind.core.TaxRegistrationStatus;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityCriteria;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityRowView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionCriteria;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.BookkeepingReportStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused coverage tests for private statement-service branches and ordering seams. */
class BookkeepingStatementServiceCoverageTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-05-13T11:00:00Z");
  private static final LocalDate PERIOD_FROM = LocalDate.parse("2026-04-07");
  private static final LocalDate PERIOD_TO = LocalDate.parse("2026-04-08");

  @Test
  void comparators_breakTiesByCurrencyCode() {
    CurrencyBalance eurDebit = balance("EUR", "1.00", "0.00");
    CurrencyBalance usdDebit = balance("USD", "1.00", "0.00");
    FinancialPositionRowView eurPositionRow =
        financialPositionRowView("1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY, eurDebit);
    FinancialPositionRowView usdPositionRow =
        financialPositionRowView("1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY, usdDebit);
    IncomeStatementRowView eurIncomeRow =
        incomeStatementRowView(
            "4000", "Sales", AccountType.REVENUE, AccountRole.ORDINARY, eurDebit);
    IncomeStatementRowView usdIncomeRow =
        incomeStatementRowView(
            "4000", "Sales", AccountType.REVENUE, AccountRole.ORDINARY, usdDebit);
    ChangesInEquityRowView eurEquityRow =
        equityRowView("3000", "Capital", AccountRole.ORDINARY, eurDebit, eurDebit, eurDebit);
    ChangesInEquityRowView usdEquityRow =
        equityRowView("3000", "Capital", AccountRole.ORDINARY, usdDebit, usdDebit, usdDebit);

    assertTrue(BookkeepingStatementService.BALANCE_ORDER.compare(eurDebit, usdDebit) < 0);
    assertTrue(
        BookkeepingStatementService.FINANCIAL_POSITION_ROW_ORDER.compare(
                eurPositionRow, usdPositionRow)
            < 0);
    assertTrue(
        BookkeepingStatementService.INCOME_STATEMENT_ROW_ORDER.compare(eurIncomeRow, usdIncomeRow)
            < 0);
    assertTrue(
        BookkeepingStatementService.CHANGES_IN_EQUITY_ROW_ORDER.compare(eurEquityRow, usdEquityRow)
            < 0);
  }

  @Test
  void changesInEquity_usesOpeningAndMovementFallbacksAndSkipsNonEquityRows() {
    RegisteredAccount assetAccount =
        account("1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY);
    RegisteredAccount openingEquityAccount =
        account("3000", "Owner Capital", AccountType.EQUITY, AccountRole.ORDINARY);
    RegisteredAccount movementEquityAccount =
        account("3010", "Reserve", AccountType.EQUITY, AccountRole.ORDINARY);
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
        new BookkeepingStatementService(store, store)
            .changesInEquity(new ChangesInEquityCriteria(PERIOD_FROM, PERIOD_TO));

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
    BookkeepingStatementService missingService =
        new BookkeepingStatementService(
            new CoverageBookStore(new BookLifecycleInspection.Missing(2), Map.of()),
            new CoverageBookStore(new BookLifecycleInspection.Missing(2), Map.of()));
    BookkeepingStatementService existingService =
        new BookkeepingStatementService(
            new CoverageBookStore(
                new BookLifecycleInspection.Existing(
                    BookLifecycleInspection.Status.BLANK_SQLITE, 0, 0, 2),
                Map.of()),
            new CoverageBookStore(
                new BookLifecycleInspection.Existing(
                    BookLifecycleInspection.Status.BLANK_SQLITE, 0, 0, 2),
                Map.of()));

    IllegalStateException missingFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                missingService.financialPosition(new FinancialPositionCriteria(Optional.empty())));
    IllegalStateException existingFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                existingService.financialPosition(new FinancialPositionCriteria(Optional.empty())));

    assertEquals(
        "Statement computation requires one initialized book.", missingFailure.getMessage());
    assertEquals(
        "Statement computation requires one initialized book.", existingFailure.getMessage());
  }

  @Test
  void financialPosition_withoutAsOfDate_omitsComparativeSections() {
    RegisteredAccount cash = account("1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY);
    RegisteredAccount capital =
        account("3000", "Capital", AccountType.EQUITY, AccountRole.ORDINARY);
    CoverageBookStore store =
        new CoverageBookStore(
            initializedInspection(),
            Map.of(
                queryKey(EffectiveDateRange.of(null, null), PostingCoverage.ALL_POSTING_KINDS),
                List.of(totals(cash, "EUR", 1000L, 0L), totals(capital, "EUR", 0L, 1000L))));

    FinancialPositionView view =
        new BookkeepingStatementService(store, store)
            .financialPosition(new FinancialPositionCriteria(Optional.empty()));

    assertEquals(EffectiveDateRange.of(null, null), view.comparativeEffectiveDateRange());
    assertTrue(view.comparativeSections().isEmpty());
  }

  @Test
  void privateAssertions_reportMissingSectionsAndTreatZeroBalancesAsSignedZero() {
    IllegalStateException missingSectionFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                BookkeepingStatementService.assertAccountingEquation(
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
        BookkeepingStatementService.signedMinorUnits(
            BalanceMath.currencyBalance(CurrencyUnit.of("EUR"), 0L, 0L)));
  }

  @Test
  void comparativeWindows_followFiscalYearAnchorInsteadOfBlindCalendarSubtraction() {
    BookIdentity fiscalYearShiftedIdentity =
        new BookIdentity(
            new EntityProfile(
                new BookEntityName("Shifted Year Shop"),
                EntityForm.FREELANCER,
                OwnerModel.SOLE_OWNER,
                ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
                TaxRegistrationStatus.UNSPECIFIED,
                List.of()),
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("02-29"),
            AccountingBasis.ACCRUAL,
            TaxProfile.empty());
    CoverageBookStore store =
        new CoverageBookStore(
            new BookLifecycleInspection.Initialized(
                1001, 2, 2, FIXED_INSTANT, fiscalYearShiftedIdentity),
            Map.of());
    BookkeepingStatementService service = new BookkeepingStatementService(store, store);

    FinancialPositionCriteria financialPositionCriteria =
        new FinancialPositionCriteria(Optional.of(LocalDate.parse("2025-02-28")));
    IncomeStatementCriteria incomeStatementCriteria =
        new IncomeStatementCriteria(LocalDate.parse("2025-02-28"), LocalDate.parse("2025-03-01"));

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
                    incomeStatementCriteria.effectiveDateTo()))
            .comparativeEffectiveDateRange());
  }

  private static RegisteredAccount account(
      String code, String name, AccountType accountType, AccountRole accountRole) {
    return new RegisteredAccount(
        new AccountCode(code),
        new AccountName(name),
        accountType,
        accountRole,
        accountTaxonomy(accountType),
        true,
        FIXED_INSTANT);
  }

  private static FinancialPositionRowView financialPositionRowView(
      String lineCode,
      String lineName,
      AccountType lineType,
      AccountRole lineRole,
      CurrencyBalance balance) {
    return new FinancialPositionRowView(
        lineCode,
        lineName,
        lineType,
        Optional.of(lineRole),
        FinancialPositionLineClassification.CURRENT_ASSET,
        StatementLineKind.DECLARED_ACCOUNT,
        balance);
  }

  private static IncomeStatementRowView incomeStatementRowView(
      String lineCode,
      String lineName,
      AccountType lineType,
      AccountRole lineRole,
      CurrencyBalance movement) {
    return new IncomeStatementRowView(
        lineCode,
        lineName,
        lineType,
        Optional.of(lineRole),
        ProfitAndLossLineClassification.OPERATING_REVENUE,
        StatementLineKind.DECLARED_ACCOUNT,
        movement);
  }

  private static ChangesInEquityRowView equityRowView(
      String lineCode,
      String lineName,
      AccountRole lineRole,
      CurrencyBalance openingBalance,
      CurrencyBalance movement,
      CurrencyBalance closingBalance) {
    return new ChangesInEquityRowView(
        lineCode,
        lineName,
        Optional.of(AccountType.EQUITY),
        Optional.of(lineRole),
        FinancialPositionLineClassification.OWNER_CAPITAL,
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
  private static final class CoverageBookStore
      implements BookLifecycleReader, BookkeepingReportStore {
    private final BookLifecycleInspection inspection;
    private final Map<QueryKey, List<AccountCurrencyTotals>> totalsByQuery;

    private CoverageBookStore(
        BookLifecycleInspection inspection,
        Map<QueryKey, List<AccountCurrencyTotals>> totalsByQuery) {
      this.inspection = inspection;
      this.totalsByQuery = totalsByQuery;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return inspection;
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
