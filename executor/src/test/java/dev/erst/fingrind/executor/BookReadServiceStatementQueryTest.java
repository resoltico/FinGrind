package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.currencyBalance;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.line;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.readService;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.TEST_AUTHORIZER;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
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
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.CurrencyBalance;
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
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** End-to-end report-query coverage for the accounting statement surface. */
class BookReadServiceStatementQueryTest {
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  private static final LocalDate OPENING_DATE = LocalDate.parse("2026-04-01");
  private static final LocalDate PERIOD_DATE = LocalDate.parse("2026-04-07");
  private static final ReportingPeriod TRANSFER_PERIOD_RESULT =
      new ReportingPeriod(OPENING_DATE, PERIOD_DATE);

  private static final AccountCode CASH_ACCOUNT_CODE = new AccountCode("1000");
  private static final AccountCode PETTY_CASH_ACCOUNT_CODE = new AccountCode("1010");
  private static final AccountCode EQUIPMENT_ACCOUNT_CODE = new AccountCode("1500");
  private static final AccountCode LOAN_ACCOUNT_CODE = new AccountCode("2100");
  private static final AccountCode CAPITAL_ACCOUNT_CODE = new AccountCode("3000");
  private static final AccountCode RESULT_HOLDING_ACCOUNT_CODE = new AccountCode("3200");
  private static final AccountCode REVENUE_ACCOUNT_CODE = new AccountCode("4000");
  private static final AccountCode EXPENSE_ACCOUNT_CODE = new AccountCode("5000");

  @Test
  void statementQueries_rejectUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = readService(bookSession);

      assertEquals(
          new FinancialPositionResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.financialPosition(
              new FinancialPositionQuery(Optional.of(PERIOD_DATE), ComparativeSelection.none())));
      assertEquals(
          new IncomeStatementResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.incomeStatement(
              new IncomeStatementQuery(PERIOD_DATE, PERIOD_DATE, ComparativeSelection.none())));
      assertEquals(
          new ChangesInEquityResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.changesInEquity(
              new ChangesInEquityQuery(PERIOD_DATE, PERIOD_DATE, ComparativeSelection.none())));
      assertEquals(
          new CashFlowStatementResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.cashFlowStatement(
              new CashFlowStatementQuery(PERIOD_DATE, PERIOD_DATE, ComparativeSelection.none())));
    }
  }

  @Test
  void cashFlowStatement_reportsBasisMovementBySectionAndIgnoresInternalCashTransfers() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareStatementAccounts(bookSession);
      declareAccount(bookSession, PETTY_CASH_ACCOUNT_CODE, "Petty Cash", AccountType.ASSET);
      declareAccount(
          bookSession,
          EQUIPMENT_ACCOUNT_CODE,
          "Equipment",
          AccountType.ASSET,
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.NONCURRENT_ASSET),
              Optional.empty(),
              Optional.of(CashFlowAssetClassification.NON_CASH)));
      declareAccount(
          bookSession,
          LOAN_ACCOUNT_CODE,
          "Term Loan",
          AccountType.LIABILITY,
          financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_LIABILITY));
      commitPosting(
          bookSession,
          "posting-opening-capital",
          "idem-opening-capital",
          OPENING_DATE,
          List.of(
              line("1000", JournalLine.EntrySide.DEBIT, "100.00"),
              line("3000", JournalLine.EntrySide.CREDIT, "100.00")));
      commitPosting(
          bookSession,
          "posting-opening-loan",
          "idem-opening-loan",
          OPENING_DATE,
          List.of(
              line("1000", JournalLine.EntrySide.DEBIT, "60.00"),
              line("2100", JournalLine.EntrySide.CREDIT, "60.00")));
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
          "posting-expense",
          "idem-expense",
          PERIOD_DATE,
          List.of(
              line("5000", JournalLine.EntrySide.DEBIT, "40.00"),
              line("1000", JournalLine.EntrySide.CREDIT, "40.00")));
      commitPosting(
          bookSession,
          "posting-equipment",
          "idem-equipment",
          PERIOD_DATE,
          List.of(
              line("1500", JournalLine.EntrySide.DEBIT, "30.00"),
              line("1000", JournalLine.EntrySide.CREDIT, "30.00")));
      commitPosting(
          bookSession,
          "posting-owner-contribution",
          "idem-owner-contribution",
          PERIOD_DATE,
          List.of(
              line("1000", JournalLine.EntrySide.DEBIT, "50.00"),
              line("3000", JournalLine.EntrySide.CREDIT, "50.00")));
      commitPosting(
          bookSession,
          "posting-loan-payment",
          "idem-loan-payment",
          PERIOD_DATE,
          List.of(
              line("2100", JournalLine.EntrySide.DEBIT, "20.00"),
              line("1000", JournalLine.EntrySide.CREDIT, "20.00")));
      commitPosting(
          bookSession,
          "posting-cash-transfer",
          "idem-cash-transfer",
          PERIOD_DATE,
          List.of(
              line("1010", JournalLine.EntrySide.DEBIT, "15.00"),
              line("1000", JournalLine.EntrySide.CREDIT, "15.00")));

      CashFlowStatementReport report =
          assertInstanceOf(
                  CashFlowStatementResult.Reported.class,
                  readService(bookSession)
                      .cashFlowStatement(
                          new CashFlowStatementQuery(
                              PERIOD_DATE, PERIOD_DATE, ComparativeSelection.none())))
              .report();

      assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, report.postingCoverage());
      assertEquals(
          List.of(currencyBalance("EUR", "160.00", "0.00", "160.00", BalanceSide.DEBIT)),
          report.openingCashTotals());
      assertEquals(
          List.of(currencyBalance("EUR", "170.00", "90.00", "80.00", BalanceSide.DEBIT)),
          report.movementTotals());
      assertEquals(
          List.of(currencyBalance("EUR", "240.00", "0.00", "240.00", BalanceSide.DEBIT)),
          report.closingCashTotals());
      assertEquals(
          List.of(
              cashFlowSection(
                  CashFlowSectionKind.OPERATING,
                  List.of(
                      cashFlowNominalRow(
                          "4000",
                          "Sales Revenue",
                          AccountType.REVENUE,
                          ProfitAndLossLineClassification.OPERATING_REVENUE,
                          currencyBalance("EUR", "120.00", "0.00", "120.00", BalanceSide.DEBIT)),
                      cashFlowNominalRow(
                          "5000",
                          "Operating Expense",
                          AccountType.EXPENSE,
                          ProfitAndLossLineClassification.OPERATING_EXPENSE,
                          currencyBalance("EUR", "0.00", "40.00", "40.00", BalanceSide.CREDIT))),
                  List.of(currencyBalance("EUR", "120.00", "40.00", "80.00", BalanceSide.DEBIT))),
              cashFlowSection(
                  CashFlowSectionKind.INVESTING,
                  List.of(
                      cashFlowBalanceSheetRow(
                          "1500",
                          "Equipment",
                          AccountType.ASSET,
                          FinancialPositionLineClassification.NONCURRENT_ASSET,
                          currencyBalance("EUR", "0.00", "30.00", "30.00", BalanceSide.CREDIT))),
                  List.of(currencyBalance("EUR", "0.00", "30.00", "30.00", BalanceSide.CREDIT))),
              cashFlowSection(
                  CashFlowSectionKind.FINANCING,
                  List.of(
                      cashFlowBalanceSheetRow(
                          "2100",
                          "Term Loan",
                          AccountType.LIABILITY,
                          FinancialPositionLineClassification.NONCURRENT_LIABILITY,
                          currencyBalance("EUR", "0.00", "20.00", "20.00", BalanceSide.CREDIT)),
                      cashFlowBalanceSheetRow(
                          "3000",
                          "Owner Capital",
                          AccountType.EQUITY,
                          FinancialPositionLineClassification.OTHER_EQUITY,
                          currencyBalance("EUR", "50.00", "0.00", "50.00", BalanceSide.DEBIT))),
                  List.of(currencyBalance("EUR", "50.00", "20.00", "30.00", BalanceSide.DEBIT)))),
          report.sections());
      assertEquals(EffectiveDateRange.unbounded(), report.comparativeEffectiveDateRange());
      assertEquals(List.of(), report.comparativeSections());
      assertEquals(List.of(), report.comparativeOpeningCashTotals());
      assertEquals(List.of(), report.comparativeMovementTotals());
      assertEquals(List.of(), report.comparativeClosingCashTotals());
      assertEquals(
          List.of("4000", "5000", "1500", "2100", "3000"),
          report.sections().stream()
              .flatMap(section -> section.rows().stream())
              .map(CashFlowRow::lineCode)
              .toList());
    }
  }

  @Test
  void financialPosition_beforeClose_reportsContraAssetsAndCurrentEarnings() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareStatementAccounts(bookSession);
      seedStatementPostings(bookSession);

      FinancialPositionResult result =
          readService(bookSession)
              .financialPosition(
                  new FinancialPositionQuery(
                      Optional.of(PERIOD_DATE), ComparativeSelection.none()));
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
                  currencyBalance("220.00", "40.00", "180.00", BalanceSide.DEBIT))),
          assetSection.rows());
      assertEquals(
          List.of(currencyBalance("220.00", "40.00", "180.00", BalanceSide.DEBIT)),
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
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT)),
              syntheticPositionRow(
                  "current-period-result",
                  "Current Period Result",
                  AccountType.EQUITY,
                  currencyBalance("0.00", "80.00", "80.00", BalanceSide.CREDIT))),
          equitySection.rows());
      assertEquals(
          List.of(currencyBalance("0.00", "180.00", "180.00", BalanceSide.CREDIT)),
          equitySection.totals());
    }
  }

  @Test
  void
      interimResultSweep_rollsIncomeIntoRetainedEarningsAndLeavesIncomeStatementOnStandardPostings() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareStatementAccounts(bookSession);
      seedStatementPostings(bookSession);
      InterimResultSweepService closeService =
          new InterimResultSweepService(
              bookSession,
              bookSession,
              () -> new PostingId("0485e481-7f56-30fd-92e2-92a099a486af"),
              FIXED_CLOCK);

      InterimResultSweepOutcome outcome =
          closeService.interimResultSweep(TRANSFER_PERIOD_RESULT, TEST_AUTHORIZER);
      dev.erst.fingrind.executor.bookkeeping.SweptInterimResult sweptInterimResult =
          assertInstanceOf(InterimResultSweepOutcome.Transferred.class, outcome)
              .sweptInterimResult();

      assertEquals(1, sweptInterimResult.sweepOrder());
      assertEquals(TRANSFER_PERIOD_RESULT, sweptInterimResult.reportingPeriod());
      assertEquals(FIXED_INSTANT, sweptInterimResult.sweptAt());
      assertEquals(1, sweptInterimResult.sweepPostingIds().size());

      BookReadService readService = readService(bookSession);

      IncomeStatementReport incomeStatement =
          assertInstanceOf(
                  IncomeStatementResult.Reported.class,
                  readService.incomeStatement(
                      new IncomeStatementQuery(
                          PERIOD_DATE, PERIOD_DATE, ComparativeSelection.none())))
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
                          currencyBalance("0.00", "120.00", "120.00", BalanceSide.CREDIT))),
                  List.of(currencyBalance("0.00", "120.00", "120.00", BalanceSide.CREDIT))),
              new IncomeStatementSection(
                  AccountType.EXPENSE,
                  List.of(
                      incomeRow(
                          "5000",
                          "Operating Expense",
                          AccountType.EXPENSE,
                          currencyBalance("40.00", "0.00", "40.00", BalanceSide.DEBIT))),
                  List.of(currencyBalance("40.00", "0.00", "40.00", BalanceSide.DEBIT)))),
          incomeStatement.sections());
      assertEquals(
          List.of(currencyBalance("0.00", "80.00", "80.00", BalanceSide.CREDIT)),
          incomeStatement.netIncomeTotals());

      FinancialPositionReport financialPosition =
          assertInstanceOf(
                  FinancialPositionResult.Reported.class,
                  readService.financialPosition(
                      new FinancialPositionQuery(
                          Optional.of(PERIOD_DATE), ComparativeSelection.none())))
              .report();
      FinancialPositionSection equitySection = financialPosition.sections().get(2);
      assertEquals(
          List.of(
              positionRow(
                  "3000",
                  "Owner Capital",
                  AccountType.EQUITY,
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT)),
              positionRow(
                  "3200",
                  "Retained Earnings",
                  AccountType.EQUITY,
                  FinancialPositionLineClassification.RESULT_HOLDING,
                  currencyBalance("0.00", "80.00", "80.00", BalanceSide.CREDIT))),
          equitySection.rows());

      ChangesInEquityReport changesInEquity =
          assertInstanceOf(
                  ChangesInEquityResult.Reported.class,
                  readService.changesInEquity(
                      new ChangesInEquityQuery(
                          PERIOD_DATE, PERIOD_DATE, ComparativeSelection.none())))
              .report();
      assertEquals(PostingCoverage.ALL_POSTING_KINDS, changesInEquity.postingCoverage());
      assertEquals(
          List.of(
              equityRow(
                  "3000",
                  "Owner Capital",
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT),
                  currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT)),
              equityRow(
                  "3200",
                  "Retained Earnings",
                  FinancialPositionLineClassification.RESULT_HOLDING,
                  currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("0.00", "80.00", "80.00", BalanceSide.CREDIT),
                  currencyBalance("0.00", "80.00", "80.00", BalanceSide.CREDIT))),
          changesInEquity.rows());
      assertEquals(
          List.of(currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT)),
          changesInEquity.openingTotals());
      assertEquals(
          List.of(currencyBalance("0.00", "80.00", "80.00", BalanceSide.CREDIT)),
          changesInEquity.movementTotals());
      assertEquals(
          List.of(currencyBalance("0.00", "180.00", "180.00", BalanceSide.CREDIT)),
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
                  service.financialPosition(
                      new FinancialPositionQuery(
                          Optional.of(PERIOD_DATE), ComparativeSelection.none())))
              .report();
      assertEquals(PostingCoverage.ALL_POSTING_KINDS, financialPosition.postingCoverage());
      assertEquals(
          List.of(
              positionRow(
                  "1000",
                  "Cash",
                  AccountType.ASSET,
                  currencyBalance("EUR", "100.00", "10.00", "90.00", BalanceSide.DEBIT))),
          financialPosition.sections().getFirst().rows());
      assertEquals(
          List.of(
              positionRow(
                  "3000",
                  "Owner Capital",
                  AccountType.EQUITY,
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
                  service.incomeStatement(
                      new IncomeStatementQuery(
                          PERIOD_DATE, PERIOD_DATE, ComparativeSelection.none())))
              .report();
      assertEquals(PostingCoverage.NON_CLOSING_POSTINGS, incomeStatement.postingCoverage());
      assertEquals(List.of(), incomeStatement.sections().getFirst().rows());
      assertEquals(
          List.of(
              incomeRow(
                  "5000",
                  "Operating Expense",
                  AccountType.EXPENSE,
                  currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))),
          incomeStatement.sections().get(1).rows());
      assertEquals(
          List.of(currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
          incomeStatement.netIncomeTotals());

      ChangesInEquityReport changesInEquity =
          assertInstanceOf(
                  ChangesInEquityResult.Reported.class,
                  service.changesInEquity(
                      new ChangesInEquityQuery(
                          PERIOD_DATE, PERIOD_DATE, ComparativeSelection.none())))
              .report();
      assertEquals(PostingCoverage.ALL_POSTING_KINDS, changesInEquity.postingCoverage());
      assertEquals(
          List.of(
              equityRow(
                  "3000",
                  "Owner Capital",
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
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
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
                                new FinancialPositionQuery(
                                    Optional.of(PERIOD_DATE), ComparativeSelection.none())))
                    .report());
    assertEquals(
        "Financial position violates the accounting equation for currency EUR.",
        failure.getMessage());
    assertEquals(
        List.of(),
        assertInstanceOf(
                IncomeStatementResult.Reported.class,
                new BookReadService(bookStore)
                    .incomeStatement(
                        new IncomeStatementQuery(
                            PERIOD_DATE, PERIOD_DATE, ComparativeSelection.none())))
            .report()
            .netIncomeTotals());
  }

  private static void declareStatementAccounts(InMemoryBookSession bookSession) {
    declareAccount(bookSession, CASH_ACCOUNT_CODE, "Cash", AccountType.ASSET);
    declareAccount(bookSession, CAPITAL_ACCOUNT_CODE, "Owner Capital", AccountType.EQUITY);
    declareAccount(
        bookSession,
        RESULT_HOLDING_ACCOUNT_CODE,
        "Retained Earnings",
        AccountType.EQUITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING));
    declareAccount(bookSession, REVENUE_ACCOUNT_CODE, "Sales Revenue", AccountType.REVENUE);
    declareAccount(bookSession, EXPENSE_ACCOUNT_CODE, "Operating Expense", AccountType.EXPENSE);
  }

  private static void declareAccount(
      InMemoryBookSession bookSession,
      AccountCode accountCode,
      String accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy) {
    assertInstanceOf(
        AccountDeclarationOutcome.Declared.class,
        bookSession.declareAccount(
            accountCode,
            new AccountName(accountName),
            accountType,
            accountTaxonomy,
            FIXED_INSTANT));
  }

  private static void declareAccount(
      InMemoryBookSession bookSession,
      AccountCode accountCode,
      String accountName,
      AccountType accountType) {
    declareAccount(
        bookSession, accountCode, accountName, accountType, accountTaxonomy(accountType));
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
      String postingId,
      PostingKind postingKind,
      dev.erst.fingrind.core.PostingOriginKind postingOriginKind,
      LocalDate effectiveDate,
      List<JournalLine> lines) {
    return new CommittedPosting(
        new PostingId(
            java.util
                .UUID
                .nameUUIDFromBytes(
                    ("fingrind-test-postingid:" + postingId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()),
        new JournalEntry(effectiveDate, lines),
        PostingLineageModel.direct(),
        postingKind,
        postingOriginKind,
        postingEvidence(postingId, postingKind),
        new CommittedProvenance(
            new RequestProvenance(
                dev.erst.fingrind.executor.ScenarioCommandIdentifiers.fromLabel(
                    "command-" + postingId),
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
            new PostingId(
                java.util
                    .UUID
                    .nameUUIDFromBytes(
                        ("fingrind-test-postingid:" + postingId)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString()),
            new JournalEntry(effectiveDate, lines),
            PostingLineageModel.direct(),
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
            accountingEvidence(idempotencyKey),
            new CommittedProvenance(
                new RequestProvenance(
                    dev.erst.fingrind.executor.ScenarioCommandIdentifiers.fromLabel(
                        "command-" + postingId),
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
    if (postingKind == PostingKind.INTERIM_RESULT_SWEEP) {
      return generatedEvidence(token, "interim-result-sweep-plan");
    }
    return accountingEvidence("idem-" + token);
  }

  private static FinancialPositionRow positionRow(
      String lineCode, String lineName, AccountType lineType, CurrencyBalance balance) {
    return positionRow(
        lineCode, lineName, lineType, financialPositionClassification(lineType), balance);
  }

  private static FinancialPositionRow positionRow(
      String lineCode,
      String lineName,
      AccountType lineType,
      FinancialPositionLineClassification lineClassification,
      CurrencyBalance balance) {
    return new FinancialPositionRow(
        lineCode,
        lineName,
        lineType,
        Optional.of(lineClassification),
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
        StatementLineKind.CURRENT_PERIOD_RESULT,
        balance);
  }

  private static IncomeStatementRow incomeRow(
      String lineCode, String lineName, AccountType lineType, CurrencyBalance movement) {
    return new IncomeStatementRow(
        lineCode,
        lineName,
        lineType,
        profitAndLossClassification(lineType),
        StatementLineKind.DECLARED_ACCOUNT,
        movement);
  }

  private static ChangesInEquityRow equityRow(
      String lineCode,
      String lineName,
      CurrencyBalance openingBalance,
      CurrencyBalance movement,
      CurrencyBalance closingBalance) {
    return equityRow(
        lineCode,
        lineName,
        FinancialPositionLineClassification.OTHER_EQUITY,
        openingBalance,
        movement,
        closingBalance);
  }

  private static ChangesInEquityRow equityRow(
      String lineCode,
      String lineName,
      FinancialPositionLineClassification lineClassification,
      CurrencyBalance openingBalance,
      CurrencyBalance movement,
      CurrencyBalance closingBalance) {
    return new ChangesInEquityRow(
        lineCode,
        lineName,
        Optional.of(AccountType.EQUITY),
        Optional.of(lineClassification),
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
        StatementLineKind.CURRENT_PERIOD_RESULT,
        openingBalance,
        movement,
        closingBalance);
  }

  private static CashFlowSection cashFlowSection(
      CashFlowSectionKind sectionKind, List<CashFlowRow> rows, List<CurrencyBalance> totals) {
    return new CashFlowSection(sectionKind, rows, totals);
  }

  private static CashFlowRow cashFlowNominalRow(
      String lineCode,
      String lineName,
      AccountType lineType,
      ProfitAndLossLineClassification lineClassification,
      CurrencyBalance movement) {
    return new CashFlowRow(
        lineCode,
        lineName,
        lineType,
        Optional.empty(),
        Optional.of(lineClassification),
        StatementLineKind.DECLARED_ACCOUNT,
        movement);
  }

  private static CashFlowRow cashFlowBalanceSheetRow(
      String lineCode,
      String lineName,
      AccountType lineType,
      FinancialPositionLineClassification lineClassification,
      CurrencyBalance movement) {
    return new CashFlowRow(
        lineCode,
        lineName,
        lineType,
        Optional.of(lineClassification),
        Optional.empty(),
        StatementLineKind.DECLARED_ACCOUNT,
        movement);
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
}
