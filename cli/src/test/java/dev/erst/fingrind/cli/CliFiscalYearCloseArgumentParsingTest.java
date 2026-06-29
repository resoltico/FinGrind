package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused parsing tests for the fiscal-year-close CLI command. */
class CliFiscalYearCloseArgumentParsingTest extends CliArgumentParsingTestSupport {

  @Test
  void parse_supportsTextOutputForFiscalYearClose() {
    FiscalYearClose command =
        assertInstanceOf(
            FiscalYearClose.class,
            CliArguments.parse(
                new String[] {
                  "fiscal-year-close",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--period-start",
                  "2026-01-01",
                  "--period-end",
                  "2026-12-31",
                  "--output",
                  "text"
                }));

    assertEquals(OutputMode.TEXT, command.outputMode());
  }

  @Test
  void parse_returnsFiscalYearCloseForValidAdministrativeCommand() {
    FiscalYearClose command =
        assertInstanceOf(
            FiscalYearClose.class,
            CliArguments.parse(
                new String[] {
                  "fiscal-year-close",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--period-start",
                  "2026-01-01",
                  "--period-end",
                  "2026-12-31"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(LocalDate.parse("2026-01-01"), command.reportingPeriod().effectiveDateFrom());
    assertEquals(LocalDate.parse("2026-12-31"), command.reportingPeriod().effectiveDateTo());
    assertEquals(OutputMode.TEXT, command.outputMode());
  }

  @Test
  void parseFiscalYearClose_acceptsTextOutputAndRejectsUnsupportedArguments() {
    FiscalYearClose command =
        assertInstanceOf(
            FiscalYearClose.class,
            CliArguments.parse(
                new String[] {
                  "fiscal-year-close",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--period-start",
                  "2026-01-01",
                  "--period-end",
                  "2026-12-31",
                  "--output",
                  "text"
                }));
    CliReportingPeriodCommandArguments.ParsedReportingPeriodCommandArguments parsedArguments =
        CliFiscalYearCloseArguments.parseFiscalYearCloseArguments(
            List.of("--period-start", "2026-01-01", "--period-end", "2026-12-31"));
    CliArgumentsException unsupportedArgument =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliFiscalYearCloseArguments.parseFiscalYearCloseArguments(
                    List.of("--unexpected", "value")));

    assertEquals(OutputMode.TEXT, command.outputMode());
    assertEquals(
        new dev.erst.fingrind.core.ReportingPeriod(
            LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
        parsedArguments.reportingPeriod());
    assertEquals("--unexpected", unsupportedArgument.argument());
    assertEquals("Unsupported argument: --unexpected", unsupportedArgument.getMessage());
  }
}
