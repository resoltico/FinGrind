package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
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
                  "--year",
                  "2026",
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
                  "--year",
                  "2026"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(2026, command.fiscalYearLabel());
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
                  "--year",
                  "2026",
                  "--output",
                  "text"
                }));
    CliFiscalYearCloseArguments.ParsedFiscalYearCloseArguments parsedArguments =
        CliFiscalYearCloseArguments.parseFiscalYearCloseArguments(List.of("--year", "2026"));
    CliArgumentsException unsupportedArgument =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliFiscalYearCloseArguments.parseFiscalYearCloseArguments(
                    List.of("--unexpected", "value")));

    assertEquals(OutputMode.TEXT, command.outputMode());
    assertEquals(2026, parsedArguments.fiscalYearLabel());
    assertEquals("--unexpected", unsupportedArgument.argument());
    assertEquals("Unsupported argument: --unexpected", unsupportedArgument.getMessage());
  }

  @Test
  void parseFiscalYearClose_rejectsDuplicateYearArgument() {
    CliArgumentsException duplicateYear =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliFiscalYearCloseArguments.parseFiscalYearCloseArguments(
                    List.of("--year", "2026", "--year", "2027")));

    assertEquals("--year", duplicateYear.argument());
    assertEquals("Duplicate argument: --year", duplicateYear.getMessage());
  }

  @Test
  void parseFiscalYearClose_rejectsRemovedPeriodBoundaryArguments() {
    CliArgumentsException removedBoundary =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliFiscalYearCloseArguments.parseFiscalYearCloseArguments(
                    List.of("--period-start", "2026-01-01", "--period-end", "2026-12-31")));

    assertEquals("--period-start", removedBoundary.argument());
    assertEquals("Unsupported argument: --period-start", removedBoundary.getMessage());
  }
}
