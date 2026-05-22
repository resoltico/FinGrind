package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.currencyBalance;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.line;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.readService;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.runtime.BookFormatContract;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.StatementLineKind;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** End-to-end report-query coverage for the accounting statement surface. */
class BookReadServiceStatementQueryTest {
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  private static final LocalDate OPENING_DATE = LocalDate.parse("2026-04-01");
  private static final LocalDate PERIOD_DATE = LocalDate.parse("2026-04-07");
  private static final ReportingPeriod CLOSE_PERIOD =
      new ReportingPeriod(OPENING_DATE, PERIOD_DATE);

  private static final AccountCode CASH_ACCOUNT_CODE = new AccountCode("1000");
  private static final AccountCode CONTRA_ASSET_ACCOUNT_CODE = new AccountCode("1090");
  private static final AccountCode CAPITAL_ACCOUNT_CODE = new AccountCode("3000");
  private static final AccountCode RETAINED_EARNINGS_ACCOUNT_CODE = new AccountCode("3200");
  private static final AccountCode REVENUE_ACCOUNT_CODE = new AccountCode("4000");
  private static final AccountCode EXPENSE_ACCOUNT_CODE = new AccountCode("5000");

  /** One deterministic grouping key for test-only per-account per-currency totals. */
  private record AccountTotalsKey(RegisteredAccount account, CurrencyUnit currencyUnit) {}

  /** Mutable debit and credit totals for one grouped test account/currency bucket. */
  private static final class AccountTotalsAccumulator {
    private long debitMinor;
    private long creditMinor;
  }

  @Test
  void statementQueries_rejectUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = readService(bookSession);

      assertEquals(
          new FinancialPositionResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.financialPosition(new FinancialPositionQuery(Optional.of(PERIOD_DATE))));
      assertEquals(
          new IncomeStatementResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.incomeStatement(new IncomeStatementQuery(PERIOD_DATE, PERIOD_DATE)));
      assertEquals(
          new ChangesInEquityResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.changesInEquity(new ChangesInEquityQuery(PERIOD_DATE, PERIOD_DATE)));
    }
  }

  @Test
  void financialPosition_beforeClose_reportsContraAssetsAndCurrentEarnings() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareStatementAccounts(bookSession);
      seedStatementPostings(bookSession);

      FinancialPositionResult result =
          readService(bookSession)
              .financialPosition(new FinancialPositionQuery(Optional.of(PERIOD_DATE)));
      FinancialPositionReport report =
          assertInstanceOf(FinancialPositionResult.Reported.class, result).report();

      assertEquals(Optional.of(PERIOD_DATE), report.effectiveDateAsOf());
      assertEquals(PostingCoverage.ALL_POSTING_KINDS, report.postingCoverage());
      assertEquals(3, report.sections().size());

      FinancialPositionSection assetSection = report.sections().getFirst();
      FinancialPositionSection liabilitySection = report.sections().get(1);
      FinancialPositionSection equitySection = report.sections().get(2);

      assertEquals(AccountType.ASSET, assetSection.accountType());
      assertEquals(
          List.of(
              positionRow(
                  "1000",
                  "Cash",
                  AccountType.ASSET,
                  AccountRole.ORDINARY,
                  currencyBalance("220.00", "40.00", "180.00", BalanceSide.DEBIT)),
              positionRow(
                  "1090",
                  "Accumulated Depreciation",
                  AccountType.ASSET,
                  AccountRole.CONTRA,
                  currencyBalance("0.00", "5.00", "5.00", BalanceSide.CREDIT))),
          assetSection.rows());
      assertEquals(
          List.of(currencyBalance("220.00", "45.00", "175.00", BalanceSide.DEBIT)),
          assetSection.totals());

      assertEquals(AccountType.LIABILITY, liabilitySection.accountType());
      assertEquals(List.of(), liabilitySection.rows());
      assertEquals(List.of(), liabilitySection.totals());

      assertEquals(AccountType.EQUITY, equitySection.accountType());
      assertEquals(
          List.of(
              positionRow(
                  "3000",
                  "Owner Capital",
                  AccountType.EQUITY,
                  AccountRole.ORDINARY,
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT)),
              syntheticPositionRow(
                  "current-period-result",
                  "Current Period Result",
                  AccountType.EQUITY,
                  currencyBalance("0.00", "75.00", "75.00", BalanceSide.CREDIT))),
          equitySection.rows());
      assertEquals(
          List.of(currencyBalance("0.00", "175.00", "175.00", BalanceSide.CREDIT)),
          equitySection.totals());
    }
  }

  @Test
  void closePeriod_rollsIncomeIntoRetainedEarningsAndLeavesIncomeStatementOnStandardPostings() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareStatementAccounts(bookSession);
      seedStatementPostings(bookSession);
      PeriodCloseService closeService =
          new PeriodCloseService(
              bookSession,
              bookSession,
              bookSession,
              bookSession,
              () -> new PostingId("period-close-1"),
              FIXED_CLOCK);

      PeriodCloseOutcome outcome = closeService.closePeriod(CLOSE_PERIOD);
      dev.erst.fingrind.executor.bookkeeping.ClosedPeriod closedPeriod =
          assertInstanceOf(PeriodCloseOutcome.Closed.class, outcome).closedPeriod();

      assertEquals(1, closedPeriod.closeOrder());
      assertEquals(CLOSE_PERIOD, closedPeriod.reportingPeriod());
      assertEquals(FIXED_INSTANT, closedPeriod.closedAt());
      assertEquals(1, closedPeriod.closingPostingIds().size());

      BookReadService readService = readService(bookSession);

      IncomeStatementReport incomeStatement =
          assertInstanceOf(
                  IncomeStatementResult.Reported.class,
                  readService.incomeStatement(new IncomeStatementQuery(PERIOD_DATE, PERIOD_DATE)))
              .report();
      assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, incomeStatement.postingCoverage());
      assertEquals(
          List.of(
              new IncomeStatementSection(
                  AccountType.REVENUE,
                  List.of(
                      incomeRow(
                          "4000",
                          "Sales Revenue",
                          AccountType.REVENUE,
                          AccountRole.ORDINARY,
                          currencyBalance("0.00", "120.00", "120.00", BalanceSide.CREDIT))),
                  List.of(currencyBalance("0.00", "120.00", "120.00", BalanceSide.CREDIT))),
              new IncomeStatementSection(
                  AccountType.EXPENSE,
                  List.of(
                      incomeRow(
                          "5000",
                          "Operating Expense",
                          AccountType.EXPENSE,
                          AccountRole.ORDINARY,
                          currencyBalance("45.00", "0.00", "45.00", BalanceSide.DEBIT))),
                  List.of(currencyBalance("45.00", "0.00", "45.00", BalanceSide.DEBIT)))),
          incomeStatement.sections());
      assertEquals(
          List.of(currencyBalance("0.00", "75.00", "75.00", BalanceSide.CREDIT)),
          incomeStatement.netIncomeTotals());

      FinancialPositionReport financialPosition =
          assertInstanceOf(
                  FinancialPositionResult.Reported.class,
                  readService.financialPosition(
                      new FinancialPositionQuery(Optional.of(PERIOD_DATE))))
              .report();
      FinancialPositionSection equitySection = financialPosition.sections().get(2);
      assertEquals(
          List.of(
              positionRow(
                  "3200",
                  "Retained Earnings",
                  AccountType.EQUITY,
                  AccountRole.ORDINARY,
                  FinancialPositionLineClassification.RETAINED_EARNINGS,
                  currencyBalance("0.00", "75.00", "75.00", BalanceSide.CREDIT)),
              positionRow(
                  "3000",
                  "Owner Capital",
                  AccountType.EQUITY,
                  AccountRole.ORDINARY,
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT))),
          equitySection.rows());

      ChangesInEquityReport changesInEquity =
          assertInstanceOf(
                  ChangesInEquityResult.Reported.class,
                  readService.changesInEquity(new ChangesInEquityQuery(PERIOD_DATE, PERIOD_DATE)))
              .report();
      assertEquals(PostingCoverage.ALL_POSTING_KINDS, changesInEquity.postingCoverage());
      assertEquals(
          List.of(
              equityRow(
                  "3200",
                  "Retained Earnings",
                  AccountRole.ORDINARY,
                  FinancialPositionLineClassification.RETAINED_EARNINGS,
                  currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("0.00", "75.00", "75.00", BalanceSide.CREDIT),
                  currencyBalance("0.00", "75.00", "75.00", BalanceSide.CREDIT)),
              equityRow(
                  "3000",
                  "Owner Capital",
                  AccountRole.ORDINARY,
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT),
                  currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT))),
          changesInEquity.rows());
      assertEquals(
          List.of(currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT)),
          changesInEquity.openingTotals());
      assertEquals(
          List.of(currencyBalance("0.00", "75.00", "75.00", BalanceSide.CREDIT)),
          changesInEquity.movementTotals());
      assertEquals(
          List.of(currencyBalance("0.00", "175.00", "175.00", BalanceSide.CREDIT)),
          changesInEquity.closingTotals());
    }
  }

  @Test
  void statementQueries_projectLossAsDebitCurrentEarningsInsideOneBookCurrency() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareStatementAccounts(bookSession);
      commitPosting(
          bookSession,
          "posting-open-eur",
          "idem-open-eur",
          OPENING_DATE,
          List.of(
              moneyLine("1000", JournalLine.EntrySide.DEBIT, "EUR", "100.00"),
              moneyLine("3000", JournalLine.EntrySide.CREDIT, "EUR", "100.00")));
      commitPosting(
          bookSession,
          "posting-expense-eur",
          "idem-expense-eur",
          PERIOD_DATE,
          List.of(
              moneyLine("5000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
              moneyLine("1000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
      BookReadService service = readService(bookSession);

      FinancialPositionReport financialPosition =
          assertInstanceOf(
                  FinancialPositionResult.Reported.class,
                  service.financialPosition(new FinancialPositionQuery(Optional.of(PERIOD_DATE))))
              .report();
      assertEquals(PostingCoverage.ALL_POSTING_KINDS, financialPosition.postingCoverage());
      assertEquals(
          List.of(
              positionRow(
                  "1000",
                  "Cash",
                  AccountType.ASSET,
                  AccountRole.ORDINARY,
                  currencyBalance("EUR", "100.00", "10.00", "90.00", BalanceSide.DEBIT))),
          financialPosition.sections().getFirst().rows());
      assertEquals(
          List.of(
              positionRow(
                  "3000",
                  "Owner Capital",
                  AccountType.EQUITY,
                  AccountRole.ORDINARY,
                  currencyBalance("EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT)),
              syntheticPositionRow(
                  "current-period-result",
                  "Current Period Result",
                  AccountType.EQUITY,
                  currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))),
          financialPosition.sections().get(2).rows());

      IncomeStatementReport incomeStatement =
          assertInstanceOf(
                  IncomeStatementResult.Reported.class,
                  service.incomeStatement(new IncomeStatementQuery(PERIOD_DATE, PERIOD_DATE)))
              .report();
      assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, incomeStatement.postingCoverage());
      assertEquals(List.of(), incomeStatement.sections().getFirst().rows());
      assertEquals(
          List.of(
              incomeRow(
                  "5000",
                  "Operating Expense",
                  AccountType.EXPENSE,
                  AccountRole.ORDINARY,
                  currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))),
          incomeStatement.sections().get(1).rows());
      assertEquals(
          List.of(currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
          incomeStatement.netIncomeTotals());

      ChangesInEquityReport changesInEquity =
          assertInstanceOf(
                  ChangesInEquityResult.Reported.class,
                  service.changesInEquity(new ChangesInEquityQuery(PERIOD_DATE, PERIOD_DATE)))
              .report();
      assertEquals(PostingCoverage.ALL_POSTING_KINDS, changesInEquity.postingCoverage());
      assertEquals(
          List.of(
              equityRow(
                  "3000",
                  "Owner Capital",
                  AccountRole.ORDINARY,
                  currencyBalance("EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT),
                  currencyBalance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT)),
              syntheticEquityRow(
                  "current-period-result",
                  "Current Period Result",
                  currencyBalance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                  currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))),
          changesInEquity.rows());
    }
  }

  @Test
  void statementQueries_ignoreUndeclaredProfitAndLossLinesWhenComputingCurrentEarnings() {
    RegisteredAccount cashAccount =
        registeredAccount(
            CASH_ACCOUNT_CODE,
            new AccountName("Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            accountTaxonomy(AccountType.ASSET),
            true,
            FIXED_INSTANT);
    StatementBookStore bookStore =
        new StatementBookStore(
            List.of(cashAccount),
            List.of(
                posting(
                    "posting-unknown-profit",
                    PostingKind.STANDARD,
                    PERIOD_DATE,
                    List.of(
                        moneyLine("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                        moneyLine("9999", JournalLine.EntrySide.CREDIT, "EUR", "10.00")))));

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                assertInstanceOf(
                        FinancialPositionResult.Reported.class,
                        new BookReadService(bookStore)
                            .financialPosition(
                                new FinancialPositionQuery(Optional.of(PERIOD_DATE))))
                    .report());
    assertEquals(
        "Financial position violates the accounting equation for currency EUR.",
        failure.getMessage());
    assertEquals(
        List.of(),
        assertInstanceOf(
                IncomeStatementResult.Reported.class,
                new BookReadService(bookStore)
                    .incomeStatement(new IncomeStatementQuery(PERIOD_DATE, PERIOD_DATE)))
            .report()
            .netIncomeTotals());
  }

  private static void declareStatementAccounts(InMemoryBookSession bookSession) {
    declareAccount(bookSession, CASH_ACCOUNT_CODE, "Cash", AccountType.ASSET, AccountRole.ORDINARY);
    declareAccount(
        bookSession,
        CONTRA_ASSET_ACCOUNT_CODE,
        "Accumulated Depreciation",
        AccountType.ASSET,
        AccountRole.CONTRA);
    declareAccount(
        bookSession,
        CAPITAL_ACCOUNT_CODE,
        "Owner Capital",
        AccountType.EQUITY,
        AccountRole.ORDINARY);
    declareAccount(
        bookSession,
        RETAINED_EARNINGS_ACCOUNT_CODE,
        "Retained Earnings",
        AccountType.EQUITY,
        AccountRole.ORDINARY,
        financialPositionTaxonomy(FinancialPositionLineClassification.RETAINED_EARNINGS));
    declareAccount(
        bookSession,
        REVENUE_ACCOUNT_CODE,
        "Sales Revenue",
        AccountType.REVENUE,
        AccountRole.ORDINARY);
    declareAccount(
        bookSession,
        EXPENSE_ACCOUNT_CODE,
        "Operating Expense",
        AccountType.EXPENSE,
        AccountRole.ORDINARY);
  }

  private static void declareAccount(
      InMemoryBookSession bookSession,
      AccountCode accountCode,
      String accountName,
      AccountType accountType,
      AccountRole accountRole) {
    declareAccount(
        bookSession,
        accountCode,
        accountName,
        accountType,
        accountRole,
        accountTaxonomy(accountType));
  }

  private static void declareAccount(
      InMemoryBookSession bookSession,
      AccountCode accountCode,
      String accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy) {
    assertInstanceOf(
        AccountDeclarationOutcome.Declared.class,
        bookSession.declareAccount(
            accountCode,
            new AccountName(accountName),
            accountType,
            accountRole,
            accountTaxonomy,
            FIXED_INSTANT));
  }

  private static void seedStatementPostings(InMemoryBookSession bookSession) {
    commitPosting(
        bookSession,
        "posting-open",
        "idem-open",
        OPENING_DATE,
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "100.00"),
            line("3000", JournalLine.EntrySide.CREDIT, "100.00")));
    commitPosting(
        bookSession,
        "posting-sale",
        "idem-sale",
        PERIOD_DATE,
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "120.00"),
            line("4000", JournalLine.EntrySide.CREDIT, "120.00")));
    commitPosting(
        bookSession,
        "posting-expense-cash",
        "idem-expense-cash",
        PERIOD_DATE,
        List.of(
            line("5000", JournalLine.EntrySide.DEBIT, "40.00"),
            line("1000", JournalLine.EntrySide.CREDIT, "40.00")));
    commitPosting(
        bookSession,
        "posting-expense-contra",
        "idem-expense-contra",
        PERIOD_DATE,
        List.of(
            line("5000", JournalLine.EntrySide.DEBIT, "5.00"),
            line("1090", JournalLine.EntrySide.CREDIT, "5.00")));
  }

  private static JournalLine moneyLine(
      String accountCode, JournalLine.EntrySide side, String currencyCode, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse(currencyCode, amount));
  }

  private static CurrencyBalance currencyBalance(
      String debitAmount, String creditAmount, String netAmount, BalanceSide balanceSide) {
    return currencyBalance("EUR", debitAmount, creditAmount, netAmount, balanceSide);
  }

  private static CurrencyBalance currencyBalance(
      String currencyCode,
      String debitAmount,
      String creditAmount,
      String netAmount,
      BalanceSide balanceSide) {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(
            Money.parse(currencyCode, debitAmount), Money.parse(currencyCode, creditAmount));
    if (!balance.netAmount().equals(Money.parse(currencyCode, netAmount))
        || balance.balanceSide() != balanceSide) {
      throw new IllegalArgumentException("Test fixture balance does not match derived totals.");
    }
    return balance;
  }

  private static CommittedPosting posting(
      String postingId, PostingKind postingKind, LocalDate effectiveDate, List<JournalLine> lines) {
    return new CommittedPosting(
        new PostingId(postingId),
        new JournalEntry(effectiveDate, lines),
        PostingLineageModel.direct(),
        postingKind,
        postingEvidence(postingId, postingKind),
        new CommittedProvenance(
            new RequestProvenance(
                new dev.erst.fingrind.core.ActorId("actor-" + postingId),
                dev.erst.fingrind.core.ActorType.AGENT,
                new dev.erst.fingrind.core.CommandId("command-" + postingId),
                new dev.erst.fingrind.core.IdempotencyKey("idem-" + postingId),
                new dev.erst.fingrind.core.CausationId("cause-" + postingId),
                Optional.empty()),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }

  private static void commitPosting(
      InMemoryBookSession bookSession,
      String postingId,
      String idempotencyKey,
      LocalDate effectiveDate,
      List<JournalLine> lines) {
    CommittedPosting posting =
        new CommittedPosting(
            new PostingId(postingId),
            new JournalEntry(effectiveDate, lines),
            PostingLineageModel.direct(),
            PostingKind.STANDARD,
            accountingEvidence(idempotencyKey),
            new CommittedProvenance(
                new RequestProvenance(
                    new dev.erst.fingrind.core.ActorId("actor-" + postingId),
                    dev.erst.fingrind.core.ActorType.AGENT,
                    new dev.erst.fingrind.core.CommandId("command-" + postingId),
                    new dev.erst.fingrind.core.IdempotencyKey(idempotencyKey),
                    new dev.erst.fingrind.core.CausationId("cause-" + postingId),
                    Optional.empty()),
                FIXED_INSTANT,
                SourceChannel.CLI));
    assertInstanceOf(
        dev.erst.fingrind.executor.spi.PostingCommitResult.Committed.class,
        bookSession.commit(posting));
  }

  private static dev.erst.fingrind.core.AccountingEvidence postingEvidence(
      String token, PostingKind postingKind) {
    if (postingKind == PostingKind.PERIOD_CLOSE) {
      return generatedEvidence(token, "period-close-plan");
    }
    return accountingEvidence("idem-" + token);
  }

  private static FinancialPositionRow positionRow(
      String lineCode,
      String lineName,
      AccountType lineType,
      AccountRole lineRole,
      CurrencyBalance balance) {
    return positionRow(
        lineCode, lineName, lineType, lineRole, financialPositionClassification(lineType), balance);
  }

  private static FinancialPositionRow positionRow(
      String lineCode,
      String lineName,
      AccountType lineType,
      AccountRole lineRole,
      FinancialPositionLineClassification lineClassification,
      CurrencyBalance balance) {
    return new FinancialPositionRow(
        lineCode,
        lineName,
        lineType,
        Optional.of(lineRole),
        lineClassification,
        StatementLineKind.DECLARED_ACCOUNT,
        balance);
  }

  private static FinancialPositionRow syntheticPositionRow(
      String lineCode, String lineName, AccountType lineType, CurrencyBalance balance) {
    return new FinancialPositionRow(
        lineCode,
        lineName,
        lineType,
        Optional.empty(),
        FinancialPositionLineClassification.CURRENT_PERIOD_RESULT,
        StatementLineKind.CURRENT_PERIOD_RESULT,
        balance);
  }

  private static IncomeStatementRow incomeRow(
      String lineCode,
      String lineName,
      AccountType lineType,
      AccountRole lineRole,
      CurrencyBalance movement) {
    return new IncomeStatementRow(
        lineCode,
        lineName,
        lineType,
        Optional.of(lineRole),
        profitAndLossClassification(lineType),
        StatementLineKind.DECLARED_ACCOUNT,
        movement);
  }

  private static ChangesInEquityRow equityRow(
      String lineCode,
      String lineName,
      AccountRole lineRole,
      CurrencyBalance openingBalance,
      CurrencyBalance movement,
      CurrencyBalance closingBalance) {
    return equityRow(
        lineCode,
        lineName,
        lineRole,
        FinancialPositionLineClassification.OTHER_EQUITY,
        openingBalance,
        movement,
        closingBalance);
  }

  private static ChangesInEquityRow equityRow(
      String lineCode,
      String lineName,
      AccountRole lineRole,
      FinancialPositionLineClassification lineClassification,
      CurrencyBalance openingBalance,
      CurrencyBalance movement,
      CurrencyBalance closingBalance) {
    return new ChangesInEquityRow(
        lineCode,
        lineName,
        Optional.of(AccountType.EQUITY),
        Optional.of(lineRole),
        lineClassification,
        StatementLineKind.DECLARED_ACCOUNT,
        openingBalance,
        movement,
        closingBalance);
  }

  private static ChangesInEquityRow syntheticEquityRow(
      String lineCode,
      String lineName,
      CurrencyBalance openingBalance,
      CurrencyBalance movement,
      CurrencyBalance closingBalance) {
    return new ChangesInEquityRow(
        lineCode,
        lineName,
        Optional.empty(),
        Optional.empty(),
        FinancialPositionLineClassification.CURRENT_PERIOD_RESULT,
        StatementLineKind.CURRENT_PERIOD_RESULT,
        openingBalance,
        movement,
        closingBalance);
  }

  private static FinancialPositionLineClassification financialPositionClassification(
      AccountType accountType) {
    return switch (accountType) {
      case ASSET -> FinancialPositionLineClassification.CURRENT_ASSET;
      case LIABILITY -> FinancialPositionLineClassification.CURRENT_LIABILITY;
      case EQUITY -> FinancialPositionLineClassification.OTHER_EQUITY;
      case REVENUE, EXPENSE ->
          throw new IllegalArgumentException(
              "Financial-position rows require balance-sheet accounts.");
    };
  }

  private static ProfitAndLossLineClassification profitAndLossClassification(
      AccountType accountType) {
    return switch (accountType) {
      case REVENUE -> ProfitAndLossLineClassification.OPERATING_REVENUE;
      case EXPENSE -> ProfitAndLossLineClassification.OPERATING_EXPENSE;
      case ASSET, LIABILITY, EQUITY ->
          throw new IllegalArgumentException("Income-statement rows require nominal accounts.");
    };
  }

  /** Minimal in-memory statement book for targeted read-side edge cases. */
  private static final class StatementBookStore implements BookkeepingReadStore {
    private final List<RegisteredAccount> accounts;
    private final List<CommittedPosting> postings;

    private StatementBookStore(List<RegisteredAccount> accounts, List<CommittedPosting> postings) {
      this.accounts = List.copyOf(accounts);
      this.postings = List.copyOf(postings);
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return initializedLifecycleInspection(
          BookFormatContract.APPLICATION_ID,
          BookFormatContract.FORMAT_VERSION,
          BookFormatContract.FORMAT_VERSION,
          FIXED_INSTANT);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return accounts.stream()
          .filter(account -> account.accountCode().equals(accountCode))
          .findFirst();
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(
        dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return postings.stream().filter(posting -> posting.postingId().equals(postingId)).findFirst();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      return accounts;
    }

    @Override
    public List<AccountCurrencyTotals> accountTotals(
        EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
      Map<AccountTotalsKey, AccountTotalsAccumulator> groupedTotals = new ConcurrentHashMap<>();
      for (CommittedPosting posting : postings) {
        if (!effectiveDateRange.contains(posting.journalEntry().effectiveDate())
            || !postingCoverage.includes(posting.postingKind())) {
          continue;
        }
        for (JournalLine line : posting.journalEntry().lines()) {
          Optional<RegisteredAccount> account = findAccount(line.accountCode());
          if (account.isEmpty()) {
            continue;
          }
          AccountTotalsAccumulator totals =
              accountTotalsAccumulator(
                  groupedTotals, account.orElseThrow(), line.amount().currencyUnit());
          if (line.side() == JournalLine.EntrySide.DEBIT) {
            totals.debitMinor = Math.addExact(totals.debitMinor, line.amount().minorUnits());
          } else {
            totals.creditMinor = Math.addExact(totals.creditMinor, line.amount().minorUnits());
          }
        }
      }
      return groupedTotals.entrySet().stream()
          .sorted(
              java.util.Comparator.comparing(
                      (Map.Entry<AccountTotalsKey, AccountTotalsAccumulator> entry) ->
                          entry.getKey().account().accountCode().value())
                  .thenComparing(entry -> entry.getKey().currencyUnit().code()))
          .map(
              entry ->
                  new AccountCurrencyTotals(
                      entry.getKey().account(),
                      entry.getKey().currencyUnit(),
                      entry.getValue().debitMinor,
                      entry.getValue().creditMinor))
          .toList();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage listAccounts(
        dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery query) {
      throw unsupported();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage listPostings(
        dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery query) {
      throw unsupported();
    }

    @Override
    public Optional<dev.erst.fingrind.executor.bookkeeping.AccountBalanceView> accountBalance(
        dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria query) {
      throw unsupported();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.TrialBalanceView trialBalance(
        dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria query) {
      throw unsupported();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountLedgerView accountLedger(
        dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria query,
        RegisteredAccount account) {
      throw unsupported();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView periodSummary(
        dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria query) {
      throw unsupported();
    }

    private static AccountTotalsAccumulator accountTotalsAccumulator(
        Map<AccountTotalsKey, AccountTotalsAccumulator> groupedTotals,
        RegisteredAccount account,
        CurrencyUnit currencyUnit) {
      AccountTotalsKey key = new AccountTotalsKey(account, currencyUnit);
      AccountTotalsAccumulator existing = groupedTotals.get(key);
      if (existing != null) {
        return existing;
      }
      AccountTotalsAccumulator created = new AccountTotalsAccumulator();
      groupedTotals.put(key, created);
      return created;
    }

    private static AssertionError unsupported() {
      return new AssertionError("This statement test double does not support that seam.");
    }
  }
}
