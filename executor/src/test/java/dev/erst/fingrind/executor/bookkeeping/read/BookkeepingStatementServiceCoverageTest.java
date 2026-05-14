package dev.erst.fingrind.executor.bookkeeping.read;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityCriteria;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityRowView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityView;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionCriteria;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
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
        new FinancialPositionRowView(
            "1000", "Cash", AccountType.ASSET, Optional.of(AccountRole.ORDINARY), false, eurDebit);
    FinancialPositionRowView usdPositionRow =
        new FinancialPositionRowView(
            "1000", "Cash", AccountType.ASSET, Optional.of(AccountRole.ORDINARY), false, usdDebit);
    IncomeStatementRowView eurIncomeRow =
        new IncomeStatementRowView(
            "4000",
            "Sales",
            AccountType.REVENUE,
            Optional.of(AccountRole.ORDINARY),
            false,
            eurDebit);
    IncomeStatementRowView usdIncomeRow =
        new IncomeStatementRowView(
            "4000",
            "Sales",
            AccountType.REVENUE,
            Optional.of(AccountRole.ORDINARY),
            false,
            usdDebit);
    ChangesInEquityRowView eurEquityRow =
        new ChangesInEquityRowView(
            "3000",
            "Capital",
            Optional.of(AccountType.EQUITY),
            Optional.of(AccountRole.ORDINARY),
            false,
            eurDebit,
            eurDebit,
            eurDebit);
    ChangesInEquityRowView usdEquityRow =
        new ChangesInEquityRowView(
            "3000",
            "Capital",
            Optional.of(AccountType.EQUITY),
            Optional.of(AccountRole.ORDINARY),
            false,
            usdDebit,
            usdDebit,
            usdDebit);

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
        new BookkeepingStatementService(store)
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
            new CoverageBookStore(new BookLifecycleInspection.Missing(2), Map.of()));
    BookkeepingStatementService existingService =
        new BookkeepingStatementService(
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

  private static RegisteredAccount account(
      String code, String name, AccountType accountType, AccountRole accountRole) {
    return new RegisteredAccount(
        new AccountCode(code),
        new AccountName(name),
        accountType,
        accountRole,
        true,
        FIXED_INSTANT);
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
  private static final class CoverageBookStore implements BookStore {
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
    public dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome openBook(
        Instant initializedAt, BookIdentity bookIdentity) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        AccountType accountType,
        AccountRole accountRole,
        Instant declaredAt) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PostingHistoryPage listPostings(PostingHistoryQuery query) {
      throw new UnsupportedOperationException();
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

    @Override
    public PostingCommitResult commit(
        PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PeriodCloseOutcome closePeriod(
        PeriodCloseDraft periodCloseDraft, PostingIdGenerator postingIdGenerator) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(
        dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CommittedPosting> findPosting(dev.erst.fingrind.core.PostingId postingId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(
        dev.erst.fingrind.core.PostingId priorPostingId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<LocalDate> closedThroughEffectiveDate() {
      throw new UnsupportedOperationException();
    }
  }
}
