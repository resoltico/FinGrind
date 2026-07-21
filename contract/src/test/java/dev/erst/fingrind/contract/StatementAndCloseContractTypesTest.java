package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.ClosedFiscalYear;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.SweptInterimResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct contract-model coverage for statement and close-operation public bookkeeping types. */
class StatementAndCloseContractTypesTest {
  @Test
  void statementAndCloseContractTypes_preserveCanonicalPayloads() {
    FinancialPositionRow financialPositionRow =
        ContractFixtures.financialPositionRow(
            "1000",
            "Cash",
            AccountType.ASSET,
            FinancialPositionLineClassification.CURRENT_ASSET,
            StatementLineKind.DECLARED_ACCOUNT,
            balance("EUR", "15.00", "0.00"));
    FinancialPositionSection financialPositionSection =
        new FinancialPositionSection(
            AccountType.ASSET,
            new ArrayList<>(List.of(financialPositionRow)),
            new ArrayList<>(List.of(balance("EUR", "15.00", "0.00"))));
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            ContractFixtures.bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            true,
            new ArrayList<>(List.of(financialPositionSection)),
            new ArrayList<>(List.of(financialPositionSection)));
    FinancialPositionResult.Reported reportedFinancialPosition =
        new FinancialPositionResult.Reported(financialPositionReport);
    BookQueryRejection.BookNotInitialized financialPositionRejection =
        new BookQueryRejection.BookNotInitialized();
    FinancialPositionResult.Rejected rejectedFinancialPosition =
        new FinancialPositionResult.Rejected(financialPositionRejection);

    IncomeStatementRow incomeStatementRow =
        ContractFixtures.incomeStatementRow(
            "4000",
            "Revenue",
            AccountType.REVENUE,
            ProfitAndLossLineClassification.OPERATING_REVENUE,
            StatementLineKind.DECLARED_ACCOUNT,
            balance("EUR", "0.00", "10.00"));
    IncomeStatementSection incomeStatementSection =
        new IncomeStatementSection(
            AccountType.REVENUE,
            new ArrayList<>(List.of(incomeStatementRow)),
            new ArrayList<>(List.of(balance("EUR", "0.00", "10.00"))));
    IncomeStatementReport incomeStatementReport =
        new IncomeStatementReport(
            ContractFixtures.bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            new ArrayList<>(List.of(incomeStatementSection)),
            new ArrayList<>(List.of(balance("EUR", "0.00", "10.00"))),
            new ArrayList<>(List.of(incomeStatementSection)),
            new ArrayList<>(List.of(balance("EUR", "0.00", "10.00"))));
    IncomeStatementResult.Reported reportedIncomeStatement =
        new IncomeStatementResult.Reported(incomeStatementReport);
    BookQueryRejection.BookNotInitialized incomeStatementRejection =
        new BookQueryRejection.BookNotInitialized();
    IncomeStatementResult.Rejected rejectedIncomeStatement =
        new IncomeStatementResult.Rejected(incomeStatementRejection);

    ChangesInEquityRow changesRow =
        ContractFixtures.changesInEquityRow(
            "3000",
            "Owner Capital",
            Optional.of(AccountType.EQUITY),
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
            StatementLineKind.DECLARED_ACCOUNT,
            balance("EUR", "0.00", "100.00"),
            balance("EUR", "0.00", "10.00"),
            balance("EUR", "0.00", "110.00"));
    ChangesInEquityReport changesReport =
        new ChangesInEquityReport(
            ContractFixtures.bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            new ArrayList<>(List.of(changesRow)),
            new ArrayList<>(List.of(balance("EUR", "0.00", "100.00"))),
            new ArrayList<>(List.of(balance("EUR", "0.00", "10.00"))),
            new ArrayList<>(List.of(balance("EUR", "0.00", "110.00"))),
            new ArrayList<>(List.of(changesRow)),
            new ArrayList<>(List.of(balance("EUR", "0.00", "100.00"))),
            new ArrayList<>(List.of(balance("EUR", "0.00", "10.00"))),
            new ArrayList<>(List.of(balance("EUR", "0.00", "110.00"))));
    ChangesInEquityResult.Reported reportedChanges =
        new ChangesInEquityResult.Reported(changesReport);
    BookQueryRejection.BookNotInitialized changesInEquityRejection =
        new BookQueryRejection.BookNotInitialized();
    ChangesInEquityResult.Rejected rejectedChanges =
        new ChangesInEquityResult.Rejected(changesInEquityRejection);

    ReportingPeriod reportingPeriod =
        new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));
    InterimResultSweepCommand interimResultSweepCommand =
        new InterimResultSweepCommand(reportingPeriod.effectiveDateTo());
    SweptInterimResult sweptInterimResult =
        new SweptInterimResult(
            1,
            reportingPeriod,
            new AccountCode("3000"),
            new ArrayList<>(List.of(balance("EUR", "0.00", "10.00"))),
            Instant.parse("2026-05-12T12:34:56Z"),
            new ArrayList<>(List.of(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))));
    InterimResultSweepResult.Swept interimResultSweepResultSwept =
        new InterimResultSweepResult.Swept(sweptInterimResult);
    BookAdministrationRejection.BookNotInitialized interimResultSweepRejection =
        new BookAdministrationRejection.BookNotInitialized();
    InterimResultSweepResult.Rejected interimResultSweepRejected =
        new InterimResultSweepResult.Rejected(interimResultSweepRejection);
    FiscalYearCloseCommand fiscalYearCloseCommand = new FiscalYearCloseCommand(2026);
    ClosedFiscalYear closedFiscalYear =
        new ClosedFiscalYear(
            1,
            reportingPeriod,
            new AccountCode("3100"),
            new AccountCode("3200"),
            new AccountCode("3300"),
            Instant.parse("2026-12-31T23:59:59Z"),
            new ArrayList<>(
                List.of(
                    new PostingId("41a95cd2-4a5f-3ef3-8a33-c2771905f362"),
                    new PostingId("6d857901-cb53-3986-a1d7-2f64319c76ce"))));
    FiscalYearCloseResult.Closed fiscalYearCloseResultClosed =
        new FiscalYearCloseResult.Closed(closedFiscalYear, false);
    BookAdministrationRejection.BookNotInitialized fiscalYearCloseRejection =
        new BookAdministrationRejection.BookNotInitialized();
    FiscalYearCloseResult.Rejected fiscalYearCloseRejected =
        new FiscalYearCloseResult.Rejected(fiscalYearCloseRejection);

    DeclaredAccount declaredAccount =
        ContractFixtures.declaredAccount(
            "1090",
            "Owner Capital",
            AccountType.EQUITY,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    FinancialPositionQuery financialPositionQuery =
        new FinancialPositionQuery(
            Optional.of(LocalDate.parse("2026-04-30")), ComparativeSelection.none());
    IncomeStatementQuery incomeStatementQuery =
        new IncomeStatementQuery(
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            ComparativeSelection.none());
    ChangesInEquityQuery changesInEquityQuery =
        new ChangesInEquityQuery(
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            ComparativeSelection.none());

    assertEquals(
        Optional.of(LocalDate.parse("2026-04-30")), financialPositionQuery.effectiveDateAsOf());
    assertEquals(ComparativeSelection.none(), financialPositionQuery.comparativeSelection());
    assertSame(financialPositionReport, reportedFinancialPosition.report());
    assertSame(financialPositionReport, reportedFinancialPosition.reported());
    assertNull(reportedFinancialPosition.rejection());
    assertSame(financialPositionRejection, rejectedFinancialPosition.rejection());
    assertEquals(
        "reported", reportedFinancialPosition.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals(
        "rejected", rejectedFinancialPosition.fold(ignored -> "reported", ignored -> "rejected"));
    assertSame(incomeStatementReport, reportedIncomeStatement.report());
    assertSame(incomeStatementReport, reportedIncomeStatement.reported());
    assertNull(reportedIncomeStatement.rejection());
    assertSame(incomeStatementRejection, rejectedIncomeStatement.rejection());
    assertEquals(
        "reported", reportedIncomeStatement.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals(
        "rejected", rejectedIncomeStatement.fold(ignored -> "reported", ignored -> "rejected"));
    assertSame(changesReport, reportedChanges.report());
    assertSame(changesReport, reportedChanges.reported());
    assertNull(reportedChanges.rejection());
    assertSame(changesInEquityRejection, rejectedChanges.rejection());
    assertEquals("reported", reportedChanges.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals("rejected", rejectedChanges.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals(LocalDate.parse("2026-04-01"), incomeStatementQuery.effectiveDateFrom());
    assertEquals(LocalDate.parse("2026-04-30"), incomeStatementQuery.effectiveDateTo());
    assertEquals(ComparativeSelection.none(), incomeStatementQuery.comparativeSelection());
    assertEquals(LocalDate.parse("2026-04-01"), changesInEquityQuery.effectiveDateFrom());
    assertEquals(LocalDate.parse("2026-04-30"), changesInEquityQuery.effectiveDateTo());
    assertEquals(ComparativeSelection.none(), changesInEquityQuery.comparativeSelection());
    assertEquals(
        reportingPeriod.effectiveDateTo(), interimResultSweepCommand.throughEffectiveDate());
    assertSame(sweptInterimResult, interimResultSweepResultSwept.sweptInterimResult());
    assertSame(interimResultSweepRejection, interimResultSweepRejected.rejection());
    assertEquals(2026, fiscalYearCloseCommand.fiscalYearLabel());
    assertSame(closedFiscalYear, fiscalYearCloseResultClosed.closedFiscalYear());
    assertSame(fiscalYearCloseRejection, fiscalYearCloseRejected.rejection());
    assertEquals(dev.erst.fingrind.core.NormalBalance.CREDIT, declaredAccount.normalBalance());
  }

  @Test
  void statementAndCloseContractTypes_rejectInvalidInputs() {
    assertThrows(
        NullPointerException.class,
        () -> new FinancialPositionQuery(nullOf(), ComparativeSelection.none()));
    assertThrows(
        NullPointerException.class,
        () ->
            new FinancialPositionReport(
                ContractFixtures.bookIdentity(),
                Optional.empty(),
                Optional.empty(),
                EffectiveDateRange.unbounded(),
                PostingCoverage.ALL_POSTING_KINDS,
                true,
                nullOf(),
                List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new FinancialPositionSection(nullOf(), List.of(), List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new FinancialPositionRow(
                nullOf(),
                "Cash",
                AccountType.ASSET,
                Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                StatementLineKind.DECLARED_ACCOUNT,
                balance("EUR", "1.00", "0.00")));
    assertThrows(NullPointerException.class, () -> new FinancialPositionResult.Reported(nullOf()));
    assertThrows(NullPointerException.class, () -> new FinancialPositionResult.Rejected(nullOf()));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IncomeStatementQuery(
                LocalDate.parse("2026-04-30"),
                LocalDate.parse("2026-04-01"),
                ComparativeSelection.none()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IncomeStatementReport(
                ContractFixtures.bookIdentity(),
                LocalDate.parse("2026-04-30"),
                LocalDate.parse("2026-04-01"),
                EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
                PostingCoverage.NON_CLOSING_POSTINGS,
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new IncomeStatementSection(nullOf(), List.of(), List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new IncomeStatementRow(
                "4000",
                nullOf(),
                AccountType.REVENUE,
                ProfitAndLossLineClassification.OPERATING_REVENUE,
                StatementLineKind.DECLARED_ACCOUNT,
                balance("EUR", "0.00", "1.00")));
    assertThrows(NullPointerException.class, () -> new IncomeStatementResult.Reported(nullOf()));
    assertThrows(NullPointerException.class, () -> new IncomeStatementResult.Rejected(nullOf()));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ChangesInEquityQuery(
                LocalDate.parse("2026-04-30"),
                LocalDate.parse("2026-04-01"),
                ComparativeSelection.none()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ChangesInEquityReport(
                ContractFixtures.bookIdentity(),
                LocalDate.parse("2026-04-30"),
                LocalDate.parse("2026-04-01"),
                EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new ChangesInEquityRow(
                "3000",
                "Capital",
                Optional.of(AccountType.EQUITY),
                Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
                StatementLineKind.DECLARED_ACCOUNT,
                nullOf(),
                balance("EUR", "0.00", "1.00"),
                balance("EUR", "0.00", "1.00")));
    assertThrows(NullPointerException.class, () -> new ChangesInEquityResult.Reported(nullOf()));
    assertThrows(NullPointerException.class, () -> new ChangesInEquityResult.Rejected(nullOf()));

    assertThrows(NullPointerException.class, () -> new InterimResultSweepCommand(nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SweptInterimResult(
                0,
                new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                new AccountCode("3000"),
                List.of(balance("EUR", "0.00", "1.00")),
                Instant.parse("2026-05-12T12:34:56Z"),
                List.of()));
    assertThrows(NullPointerException.class, () -> new InterimResultSweepResult.Swept(nullOf()));
    assertThrows(NullPointerException.class, () -> new InterimResultSweepResult.Rejected(nullOf()));
    assertThrows(
        IllegalArgumentException.class, () -> new FiscalYearCloseCommand(Integer.MIN_VALUE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ClosedFiscalYear(
                0,
                new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
                new AccountCode("3100"),
                new AccountCode("3200"),
                new AccountCode("3300"),
                Instant.parse("2026-12-31T23:59:59Z"),
                List.of()));
    assertThrows(
        NullPointerException.class, () -> new FiscalYearCloseResult.Closed(nullOf(), false));
    assertThrows(NullPointerException.class, () -> new FiscalYearCloseResult.Rejected(nullOf()));
  }

  private static CurrencyBalance balance(
      String currencyCode, String debitAmount, String creditAmount) {
    return CurrencyBalance.ofTotals(
        Money.parse(currencyCode, debitAmount), Money.parse(currencyCode, creditAmount));
  }
}
