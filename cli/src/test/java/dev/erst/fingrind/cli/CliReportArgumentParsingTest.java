package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for report-oriented CLI argument parsing. */
class CliReportArgumentParsingTest {
  @Test
  void parse_assignsDefaultAndExplicitOutputModesForReadAndReportCommands() {
    Path bookFile = Path.of("book.sqlite");
    Path keyFile = Path.of("book.key");
    InspectBook defaultInspectBook =
        assertInstanceOf(
            InspectBook.class,
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString()
                }));
    InspectBook inspectBook =
        assertInstanceOf(
            InspectBook.class,
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "text"
                }));
    TrialBalance defaultTrialBalance =
        assertInstanceOf(
            TrialBalance.class,
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString()
                }));
    TrialBalance trialBalance =
        assertInstanceOf(
            TrialBalance.class,
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-as-of",
                  "2026-04-30",
                  "--output",
                  "csv"
                }));
    AccountLedger defaultAccountLedger =
        assertInstanceOf(
            AccountLedger.class,
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000"
                }));
    AccountLedger accountLedger =
        assertInstanceOf(
            AccountLedger.class,
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "text"
                }));
    PeriodSummary periodSummary =
        assertInstanceOf(
            PeriodSummary.class,
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "csv"
                }));
    PeriodSummary defaultPeriodSummary =
        assertInstanceOf(
            PeriodSummary.class,
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30"
                }));
    FinancialPosition defaultFinancialPosition =
        assertInstanceOf(
            FinancialPosition.class,
            CliArguments.parse(
                new String[] {
                  "financial-position",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString()
                }));
    FinancialPosition financialPosition =
        assertInstanceOf(
            FinancialPosition.class,
            CliArguments.parse(
                new String[] {
                  "financial-position",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-as-of",
                  "2026-04-30",
                  "--output",
                  "text",
                  "--pdf-out",
                  "reports/position.pdf"
                }));
    IncomeStatement incomeStatement =
        assertInstanceOf(
            IncomeStatement.class,
            CliArguments.parse(
                new String[] {
                  "income-statement",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "csv",
                  "--pdf-out",
                  "reports/income.pdf"
                }));
    ChangesInEquity changesInEquity =
        assertInstanceOf(
            ChangesInEquity.class,
            CliArguments.parse(
                new String[] {
                  "changes-in-equity",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "text",
                  "--pdf-out",
                  "reports/equity.pdf"
                }));
    assertEquals(OutputMode.JSON, defaultInspectBook.outputMode());
    assertEquals(OutputMode.TEXT, inspectBook.outputMode());
    assertEquals(OutputMode.JSON, defaultTrialBalance.output().outputMode());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-30")), trialBalance.query().effectiveDateAsOf());
    assertEquals(OutputMode.JSON, defaultAccountLedger.output().outputMode());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-01")), accountLedger.query().effectiveDateFrom());
    assertEquals(OutputMode.JSON, defaultPeriodSummary.output().outputMode());
    assertEquals(LocalDate.parse("2026-04-30"), periodSummary.query().effectiveDateTo());
    assertEquals(OutputMode.JSON, defaultFinancialPosition.output().outputMode());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-30")), financialPosition.query().effectiveDateAsOf());
    assertEquals(OutputMode.CSV, incomeStatement.output().outputMode());
    assertEquals(LocalDate.parse("2026-04-01"), incomeStatement.query().effectiveDateFrom());
    assertEquals(OutputMode.TEXT, changesInEquity.output().outputMode());
    assertEquals(LocalDate.parse("2026-04-30"), changesInEquity.query().effectiveDateTo());
  }

  @Test
  void parse_rejectsUnsupportedAndConflictingReportArguments() {
    Path bookFile = Path.of("book.sqlite");
    Path keyFile = Path.of("book.key");
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "csv"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "text",
                  "--output",
                  "json"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--limit",
                  "10"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "pdf"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "trial-balance",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-as-of",
                  "2026-04-30",
                  "--effective-date-as-of",
                  "2026-05-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--account-code",
                  "2000"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-from",
                  "2026-04-02"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--effective-date-to",
                  "2026-04-30",
                  "--effective-date-to",
                  "2026-05-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--output",
                  "text",
                  "--output",
                  "json"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--limit",
                  "10"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "account-ledger",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString()
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--account-code",
                  "1000",
                  "--account-code",
                  "2000"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-from",
                  "2026-04-02",
                  "--effective-date-to",
                  "2026-04-30"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--effective-date-to",
                  "2026-05-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "text",
                  "--output",
                  "json"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--limit",
                  "10"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "period-summary",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-to",
                  "2026-04-30"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "financial-position",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--output",
                  "pdf"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "income-statement",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "income-statement",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-from",
                  "2026-04-30",
                  "--effective-date-to",
                  "2026-04-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "changes-in-equity",
                  "--book-file",
                  bookFile.toString(),
                  "--book-key-file",
                  keyFile.toString(),
                  "--effective-date-to",
                  "2026-04-30"
                }));
  }
}
