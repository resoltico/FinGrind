package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.currencyBalance;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.line;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.readService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookStore;
import java.time.Clock;
import java.time.Instant;
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
  private static final ReportingPeriod CLOSE_PERIOD =
      new ReportingPeriod(OPENING_DATE, PERIOD_DATE);

  private static final AccountCode CASH_ACCOUNT_CODE = new AccountCode("1000");
  private static final AccountCode CONTRA_ASSET_ACCOUNT_CODE = new AccountCode("1090");
  private static final AccountCode CAPITAL_ACCOUNT_CODE = new AccountCode("3000");
  private static final AccountCode RETAINED_EARNINGS_ACCOUNT_CODE = new AccountCode("3200");
  private static final AccountCode REVENUE_ACCOUNT_CODE = new AccountCode("4000");
  private static final AccountCode EXPENSE_ACCOUNT_CODE = new AccountCode("5000");

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

      assertEquals(Optional.of(PERIOD_DATE), report.effectiveDateTo());
      assertEquals(3, report.sections().size());

      FinancialPositionSection assetSection = report.sections().getFirst();
      FinancialPositionSection liabilitySection = report.sections().get(1);
      FinancialPositionSection equitySection = report.sections().get(2);

      assertEquals(AccountType.ASSET, assetSection.accountType());
      assertEquals(
          List.of(
              new FinancialPositionRow(
                  "1000",
                  "Cash",
                  AccountType.ASSET,
                  false,
                  currencyBalance("220.00", "40.00", "180.00", BalanceSide.DEBIT)),
              new FinancialPositionRow(
                  "1090",
                  "Accumulated Depreciation",
                  AccountType.ASSET,
                  false,
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
              new FinancialPositionRow(
                  "3000",
                  "Owner Capital",
                  AccountType.EQUITY,
                  false,
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT)),
              new FinancialPositionRow(
                  "current-earnings",
                  "Current Earnings",
                  AccountType.EQUITY,
                  true,
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
      BookAdministrationService administrationService =
          new BookAdministrationService(bookSession, FIXED_CLOCK);

      PeriodCloseOutcome outcome = administrationService.closePeriod(CLOSE_PERIOD);
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
      assertEquals(
          List.of(
              new IncomeStatementSection(
                  AccountType.REVENUE,
                  List.of(
                      new IncomeStatementRow(
                          "4000",
                          "Sales Revenue",
                          AccountType.REVENUE,
                          false,
                          currencyBalance("0.00", "120.00", "120.00", BalanceSide.CREDIT))),
                  List.of(currencyBalance("0.00", "120.00", "120.00", BalanceSide.CREDIT))),
              new IncomeStatementSection(
                  AccountType.EXPENSE,
                  List.of(
                      new IncomeStatementRow(
                          "5000",
                          "Operating Expense",
                          AccountType.EXPENSE,
                          false,
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
              new FinancialPositionRow(
                  "3000",
                  "Owner Capital",
                  AccountType.EQUITY,
                  false,
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT)),
              new FinancialPositionRow(
                  "3200",
                  "Retained Earnings",
                  AccountType.EQUITY,
                  false,
                  currencyBalance("0.00", "75.00", "75.00", BalanceSide.CREDIT)),
              new FinancialPositionRow(
                  "current-earnings",
                  "Current Earnings",
                  AccountType.EQUITY,
                  true,
                  currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO))),
          equitySection.rows());

      ChangesInEquityReport changesInEquity =
          assertInstanceOf(
                  ChangesInEquityResult.Reported.class,
                  readService.changesInEquity(new ChangesInEquityQuery(PERIOD_DATE, PERIOD_DATE)))
              .report();
      assertEquals(
          List.of(
              new ChangesInEquityRow(
                  "3000",
                  "Owner Capital",
                  false,
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT),
                  currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("0.00", "100.00", "100.00", BalanceSide.CREDIT)),
              new ChangesInEquityRow(
                  "3200",
                  "Retained Earnings",
                  false,
                  currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("0.00", "75.00", "75.00", BalanceSide.CREDIT),
                  currencyBalance("0.00", "75.00", "75.00", BalanceSide.CREDIT)),
              new ChangesInEquityRow(
                  "current-earnings",
                  "Current Earnings",
                  true,
                  currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO))),
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
  void statementQueries_orderDuplicateLineCodesByCurrency_andProjectLossAsDebitCurrentEarnings() {
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
          "posting-open-usd",
          "idem-open-usd",
          OPENING_DATE,
          List.of(
              moneyLine("1000", JournalLine.EntrySide.DEBIT, "USD", "50.00"),
              moneyLine("3000", JournalLine.EntrySide.CREDIT, "USD", "50.00")));
      commitPosting(
          bookSession,
          "posting-expense-eur",
          "idem-expense-eur",
          PERIOD_DATE,
          List.of(
              moneyLine("5000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
              moneyLine("1000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
      commitPosting(
          bookSession,
          "posting-expense-usd",
          "idem-expense-usd",
          PERIOD_DATE,
          List.of(
              moneyLine("5000", JournalLine.EntrySide.DEBIT, "USD", "20.00"),
              moneyLine("1000", JournalLine.EntrySide.CREDIT, "USD", "20.00")));

      BookReadService service = readService(bookSession);

      FinancialPositionReport financialPosition =
          assertInstanceOf(
                  FinancialPositionResult.Reported.class,
                  service.financialPosition(new FinancialPositionQuery(Optional.of(PERIOD_DATE))))
              .report();
      assertEquals(
          List.of(
              new FinancialPositionRow(
                  "1000",
                  "Cash",
                  AccountType.ASSET,
                  false,
                  currencyBalance("EUR", "100.00", "10.00", "90.00", BalanceSide.DEBIT)),
              new FinancialPositionRow(
                  "1000",
                  "Cash",
                  AccountType.ASSET,
                  false,
                  currencyBalance("USD", "50.00", "20.00", "30.00", BalanceSide.DEBIT))),
          financialPosition.sections().getFirst().rows());
      assertEquals(
          List.of(
              new FinancialPositionRow(
                  "3000",
                  "Owner Capital",
                  AccountType.EQUITY,
                  false,
                  currencyBalance("EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT)),
              new FinancialPositionRow(
                  "3000",
                  "Owner Capital",
                  AccountType.EQUITY,
                  false,
                  currencyBalance("USD", "0.00", "50.00", "50.00", BalanceSide.CREDIT)),
              new FinancialPositionRow(
                  "current-earnings",
                  "Current Earnings",
                  AccountType.EQUITY,
                  true,
                  currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
              new FinancialPositionRow(
                  "current-earnings",
                  "Current Earnings",
                  AccountType.EQUITY,
                  true,
                  currencyBalance("USD", "20.00", "0.00", "20.00", BalanceSide.DEBIT))),
          financialPosition.sections().get(2).rows());

      IncomeStatementReport incomeStatement =
          assertInstanceOf(
                  IncomeStatementResult.Reported.class,
                  service.incomeStatement(new IncomeStatementQuery(PERIOD_DATE, PERIOD_DATE)))
              .report();
      assertEquals(List.of(), incomeStatement.sections().getFirst().rows());
      assertEquals(
          List.of(
              new IncomeStatementRow(
                  "5000",
                  "Operating Expense",
                  AccountType.EXPENSE,
                  false,
                  currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
              new IncomeStatementRow(
                  "5000",
                  "Operating Expense",
                  AccountType.EXPENSE,
                  false,
                  currencyBalance("USD", "20.00", "0.00", "20.00", BalanceSide.DEBIT))),
          incomeStatement.sections().get(1).rows());
      assertEquals(
          List.of(
              currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
              currencyBalance("USD", "20.00", "0.00", "20.00", BalanceSide.DEBIT)),
          incomeStatement.netIncomeTotals());

      ChangesInEquityReport changesInEquity =
          assertInstanceOf(
                  ChangesInEquityResult.Reported.class,
                  service.changesInEquity(new ChangesInEquityQuery(PERIOD_DATE, PERIOD_DATE)))
              .report();
      assertEquals(
          List.of(
              new ChangesInEquityRow(
                  "3000",
                  "Owner Capital",
                  false,
                  currencyBalance("EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT),
                  currencyBalance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT)),
              new ChangesInEquityRow(
                  "3000",
                  "Owner Capital",
                  false,
                  currencyBalance("USD", "0.00", "50.00", "50.00", BalanceSide.CREDIT),
                  currencyBalance("USD", "0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("USD", "0.00", "50.00", "50.00", BalanceSide.CREDIT)),
              new ChangesInEquityRow(
                  "current-earnings",
                  "Current Earnings",
                  true,
                  currencyBalance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                  currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
              new ChangesInEquityRow(
                  "current-earnings",
                  "Current Earnings",
                  true,
                  currencyBalance("USD", "0.00", "0.00", "0.00", BalanceSide.ZERO),
                  currencyBalance("USD", "20.00", "0.00", "20.00", BalanceSide.DEBIT),
                  currencyBalance("USD", "20.00", "0.00", "20.00", BalanceSide.DEBIT))),
          changesInEquity.rows());
    }
  }

  @Test
  void statementQueries_ignoreUndeclaredProfitAndLossLinesWhenComputingCurrentEarnings() {
    RegisteredAccount cashAccount =
        new RegisteredAccount(
            CASH_ACCOUNT_CODE,
            new AccountName("Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
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

    FinancialPositionReport financialPosition =
        assertInstanceOf(
                FinancialPositionResult.Reported.class,
                new BookReadService(bookStore)
                    .financialPosition(new FinancialPositionQuery(Optional.of(PERIOD_DATE))))
            .report();

    assertEquals(
        List.of(
            new FinancialPositionSection(
                AccountType.ASSET,
                List.of(
                    new FinancialPositionRow(
                        "1000",
                        "Cash",
                        AccountType.ASSET,
                        false,
                        currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                List.of(currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))),
            new FinancialPositionSection(AccountType.LIABILITY, List.of(), List.of()),
            new FinancialPositionSection(AccountType.EQUITY, List.of(), List.of())),
        financialPosition.sections());
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
        AccountRole.RETAINED_EARNINGS);
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
    assertInstanceOf(
        AccountDeclarationOutcome.Declared.class,
        bookSession.declareAccount(
            accountCode, new AccountName(accountName), accountType, accountRole, FIXED_INSTANT));
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

  /** Minimal in-memory statement book for targeted read-side edge cases. */
  private static final class StatementBookStore implements BookStore {
    private final List<RegisteredAccount> accounts;
    private final List<CommittedPosting> postings;

    private StatementBookStore(List<RegisteredAccount> accounts, List<CommittedPosting> postings) {
      this.accounts = List.copyOf(accounts);
      this.postings = List.copyOf(postings);
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(
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
    public List<CommittedPosting> postings(
        dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
      return postings.stream()
          .filter(posting -> effectiveDateRange.contains(posting.journalEntry().effectiveDate()))
          .toList();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return postings.stream()
          .map(posting -> posting.journalEntry().effectiveDate())
          .min(LocalDate::compareTo);
    }

    @Override
    public Optional<LocalDate> closedThroughEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome openBook(
        Instant initializedAt) {
      throw unsupported();
    }

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        AccountType accountType,
        AccountRole accountRole,
        Instant declaredAt) {
      throw unsupported();
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

    @Override
    public dev.erst.fingrind.executor.spi.PostingCommitResult commit(
        dev.erst.fingrind.executor.spi.PostingDraft postingDraft,
        dev.erst.fingrind.executor.spi.PostingIdGenerator postingIdGenerator) {
      throw unsupported();
    }

    @Override
    public PeriodCloseOutcome closePeriod(
        dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft periodCloseDraft,
        dev.erst.fingrind.executor.spi.PostingIdGenerator postingIdGenerator) {
      throw unsupported();
    }

    private static AssertionError unsupported() {
      return new AssertionError("This statement test double does not support that seam.");
    }
  }
}
