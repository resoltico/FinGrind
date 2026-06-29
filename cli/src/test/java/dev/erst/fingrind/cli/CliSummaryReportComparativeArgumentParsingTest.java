package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Focused parsing coverage for comparative summary report arguments. */
class CliSummaryReportComparativeArgumentParsingTest {
  @Test
  void parse_defaultsComparativeSelectionToNoneForSummaryReports() {
    TrialBalance trialBalance =
        assertInstanceOf(
            TrialBalance.class,
            CliArguments.parse(
                summaryReportArguments("trial-balance", "--effective-date-as-of", "2026-04-30")));
    FinancialPosition financialPosition =
        assertInstanceOf(
            FinancialPosition.class,
            CliArguments.parse(
                summaryReportArguments(
                    "financial-position", "--effective-date-as-of", "2026-04-30")));
    IncomeStatement incomeStatement =
        assertInstanceOf(
            IncomeStatement.class,
            CliArguments.parse(
                summaryReportArguments(
                    "income-statement",
                    "--period-start",
                    "2026-04-01",
                    "--period-end",
                    "2026-04-30")));
    CashFlowStatement cashFlowStatement =
        assertInstanceOf(
            CashFlowStatement.class,
            CliArguments.parse(
                summaryReportArguments(
                    "cash-flow-statement",
                    "--period-start",
                    "2026-04-01",
                    "--period-end",
                    "2026-04-30")));
    ChangesInEquity changesInEquity =
        assertInstanceOf(
            ChangesInEquity.class,
            CliArguments.parse(
                summaryReportArguments(
                    "changes-in-equity",
                    "--period-start",
                    "2026-04-01",
                    "--period-end",
                    "2026-04-30")));

    assertEquals(ComparativeSelection.none(), trialBalance.query().comparativeSelection());
    assertEquals(ComparativeSelection.none(), financialPosition.query().comparativeSelection());
    assertEquals(ComparativeSelection.none(), incomeStatement.query().comparativeSelection());
    assertEquals(ComparativeSelection.none(), cashFlowStatement.query().comparativeSelection());
    assertEquals(ComparativeSelection.none(), changesInEquity.query().comparativeSelection());
  }

  @Test
  void parse_acceptsExplicitComparativeSelectionsForSummaryReports() {
    TrialBalance trialBalanceRange =
        assertInstanceOf(
            TrialBalance.class,
            CliArguments.parse(
                summaryReportArguments(
                    "trial-balance",
                    "--effective-date-as-of",
                    "2026-04-30",
                    "--comparative",
                    "..2026-03-31",
                    "--posting-coverage",
                    "all-posting-kinds",
                    "--output",
                    "csv")));
    FinancialPosition financialPositionNone =
        assertInstanceOf(
            FinancialPosition.class,
            CliArguments.parse(
                summaryReportArguments(
                    "financial-position",
                    "--effective-date-as-of",
                    "2026-04-30",
                    "--comparative",
                    "none",
                    "--output",
                    "json",
                    "--pdf-out",
                    "reports/financial-position.pdf")));
    IncomeStatement incomeStatementRange =
        assertInstanceOf(
            IncomeStatement.class,
            CliArguments.parse(
                summaryReportArguments(
                    "income-statement",
                    "--period-start",
                    "2026-04-01",
                    "--period-end",
                    "2026-04-30",
                    "--comparative",
                    "2025-04-01..2025-04-30",
                    "--output",
                    "text")));
    CashFlowStatement cashFlowStatementPriorPeriod =
        assertInstanceOf(
            CashFlowStatement.class,
            CliArguments.parse(
                summaryReportArguments(
                    "cash-flow-statement",
                    "--period-start",
                    "2026-04-01",
                    "--period-end",
                    "2026-04-30",
                    "--comparative",
                    "prior-period",
                    "--pdf-out",
                    "reports/cash-flow-statement.pdf")));
    ChangesInEquity changesInEquityPriorPeriod =
        assertInstanceOf(
            ChangesInEquity.class,
            CliArguments.parse(
                summaryReportArguments(
                    "changes-in-equity",
                    "--period-start",
                    "2026-04-01",
                    "--period-end",
                    "2026-04-30",
                    "--comparative",
                    "prior-period",
                    "--pdf-out",
                    "reports/changes-in-equity.pdf")));

    assertEquals(
        ComparativeSelection.range(EffectiveDateRange.to(LocalDate.parse("2026-03-31"))),
        trialBalanceRange.query().comparativeSelection());
    assertEquals(OutputMode.CSV, trialBalanceRange.output().outputMode());
    assertNull(trialBalanceRange.output().pdfOutPath());
    assertEquals(ComparativeSelection.none(), financialPositionNone.query().comparativeSelection());
    assertEquals(OutputMode.JSON, financialPositionNone.output().outputMode());
    assertEquals(
        Path.of("reports/financial-position.pdf"), financialPositionNone.output().pdfOutPath());
    assertEquals(
        ComparativeSelection.range(
            EffectiveDateRange.bounded(
                LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30"))),
        incomeStatementRange.query().comparativeSelection());
    assertEquals(OutputMode.TEXT, incomeStatementRange.output().outputMode());
    assertEquals(
        ComparativeSelection.priorPeriod(),
        cashFlowStatementPriorPeriod.query().comparativeSelection());
    assertEquals(OutputMode.TEXT, cashFlowStatementPriorPeriod.output().outputMode());
    assertEquals(
        Path.of("reports/cash-flow-statement.pdf"),
        cashFlowStatementPriorPeriod.output().pdfOutPath());
    assertEquals(
        ComparativeSelection.priorPeriod(),
        changesInEquityPriorPeriod.query().comparativeSelection());
    assertEquals(OutputMode.TEXT, changesInEquityPriorPeriod.output().outputMode());
    assertEquals(
        Path.of("reports/changes-in-equity.pdf"), changesInEquityPriorPeriod.output().pdfOutPath());
  }

  @Test
  void parse_rejectsInvalidAsOfComparativeSelections() {
    CliArgumentsException missingSeparator =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    summaryReportArguments(
                        "trial-balance",
                        "--effective-date-as-of",
                        "2026-04-30",
                        "--comparative",
                        "2026-01-31")));
    CliArgumentsException invalidShape =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    summaryReportArguments(
                        "trial-balance",
                        "--effective-date-as-of",
                        "2026-04-30",
                        "--comparative",
                        "2026-01-01..2026-01-31")));
    CliArgumentsException missingUpperBound =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    summaryReportArguments(
                        "financial-position",
                        "--effective-date-as-of",
                        "2026-04-30",
                        "--comparative",
                        "..")));
    CliArgumentsException duplicateComparative =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    summaryReportArguments(
                        "trial-balance",
                        "--comparative",
                        "none",
                        "--comparative",
                        "prior-period")));

    assertEquals("--comparative", missingSeparator.argument());
    assertEquals(
        "As-of --comparative must use ..YYYY-MM-DD, none, or prior-period. Received: 2026-01-31",
        missingSeparator.getMessage());
    assertEquals("--comparative", invalidShape.argument());
    assertEquals(
        "As-of --comparative must use ..YYYY-MM-DD, none, or prior-period. Received: 2026-01-01..2026-01-31",
        invalidShape.getMessage());
    assertEquals("--comparative", missingUpperBound.argument());
    assertEquals(
        "As-of --comparative must use ..YYYY-MM-DD, none, or prior-period. Received: ..",
        missingUpperBound.getMessage());
    assertEquals("--comparative", duplicateComparative.argument());
    assertEquals("Duplicate argument: --comparative", duplicateComparative.getMessage());
  }

  @Test
  void parse_rejectsInvalidPeriodComparativeSelections() {
    CliArgumentsException missingSeparator =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    summaryReportArguments(
                        "income-statement",
                        "--period-start",
                        "2026-04-01",
                        "--period-end",
                        "2026-04-30",
                        "--comparative",
                        "2025-04-30")));
    CliArgumentsException invalidShape =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    summaryReportArguments(
                        "income-statement",
                        "--period-start",
                        "2026-04-01",
                        "--period-end",
                        "2026-04-30",
                        "--comparative",
                        "..2025-04-30")));
    CliArgumentsException missingUpperBound =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    summaryReportArguments(
                        "changes-in-equity",
                        "--period-start",
                        "2026-04-01",
                        "--period-end",
                        "2026-04-30",
                        "--comparative",
                        "2025-04-01..")));
    CliArgumentsException reversedRange =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    summaryReportArguments(
                        "changes-in-equity",
                        "--period-start",
                        "2026-04-01",
                        "--period-end",
                        "2026-04-30",
                        "--comparative",
                        "2025-04-30..2025-04-01")));
    CliArgumentsException duplicateComparative =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    summaryReportArguments(
                        "income-statement",
                        "--period-start",
                        "2026-04-01",
                        "--period-end",
                        "2026-04-30",
                        "--comparative",
                        "prior-period",
                        "--comparative",
                        "none")));

    assertEquals("--comparative", missingSeparator.argument());
    assertEquals(
        "Period --comparative must use YYYY-MM-DD..YYYY-MM-DD, none, or prior-period. Received: 2025-04-30",
        missingSeparator.getMessage());
    assertEquals("--comparative", invalidShape.argument());
    assertEquals(
        "Period --comparative must use YYYY-MM-DD..YYYY-MM-DD, none, or prior-period. Received: ..2025-04-30",
        invalidShape.getMessage());
    assertEquals("--comparative", missingUpperBound.argument());
    assertEquals(
        "Period --comparative must use YYYY-MM-DD..YYYY-MM-DD, none, or prior-period. Received: 2025-04-01..",
        missingUpperBound.getMessage());
    assertEquals("--comparative", reversedRange.argument());
    assertEquals("--comparative must be on or before --comparative.", reversedRange.getMessage());
    assertEquals("--comparative", duplicateComparative.argument());
    assertEquals("Duplicate argument: --comparative", duplicateComparative.getMessage());
  }

  @Test
  void parse_rejectsComparativeSelectionForPeriodSummary() {
    CliArgumentsException unsupportedComparative =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    summaryReportArguments(
                        "period-summary",
                        "--period-start",
                        "2026-04-01",
                        "--period-end",
                        "2026-04-30",
                        "--comparative",
                        "prior-period")));

    assertEquals("--comparative", unsupportedComparative.argument());
    assertEquals("Unsupported argument: --comparative", unsupportedComparative.getMessage());
  }

  private static String[] summaryReportArguments(String command, String... arguments) {
    String[] result = new String[arguments.length + 5];
    result[0] = command;
    result[1] = "--book-file";
    result[2] = "book.sqlite";
    result[3] = "--book-key-file";
    result[4] = "book.key";
    System.arraycopy(arguments, 0, result, 5, arguments.length);
    return result;
  }
}
