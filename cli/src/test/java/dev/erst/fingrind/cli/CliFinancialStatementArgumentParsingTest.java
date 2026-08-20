package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for financial-statement CLI argument parsing. */
class CliFinancialStatementArgumentParsingTest {
  @Test
  void parse_assignsDefaultAndExplicitOutputModesForFinancialStatements() {
    Path bookFile = Path.of("book.sqlite");
    Path keyFile = Path.of("book.key");

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
                  "--period-start",
                  "2026-04-01",
                  "--period-end",
                  "2026-04-30",
                  "--output",
                  "json",
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
                  "--period-start",
                  "2026-04-01",
                  "--period-end",
                  "2026-04-30",
                  "--output",
                  "text",
                  "--pdf-out",
                  "reports/equity.pdf"
                }));

    assertEquals(OutputMode.TEXT, defaultFinancialPosition.output().outputMode());
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-30")), financialPosition.query().effectiveDateAsOf());
    assertEquals(OutputMode.JSON, incomeStatement.output().outputMode());
    assertEquals(LocalDate.parse("2026-04-01"), incomeStatement.query().effectiveDateFrom());
    assertEquals(OutputMode.TEXT, changesInEquity.output().outputMode());
    assertEquals(LocalDate.parse("2026-04-30"), changesInEquity.query().effectiveDateTo());
  }

  @Test
  void parse_rejectsInvalidFinancialStatementArguments() {
    Path bookFile = Path.of("book.sqlite");
    Path keyFile = Path.of("book.key");

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
                  "--period-start",
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
                  "--period-start",
                  "2026-04-30",
                  "--period-end",
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
                  "--period-end",
                  "2026-04-30"
                }));
  }
}
