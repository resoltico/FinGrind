package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Focused unit tests for scalar CLI option value parsers. */
class CliOptionValuesTest {
  @Test
  void parseCurrencyUnitOption_rejectsUnsupportedCodes() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliOptionValues.parseCurrencyUnitOption("EURO", "--currency"));

    assertEquals("--currency", exception.argument());
  }

  @Test
  void parseBookEntityNameOption_rejectsInvalidEntityNames() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliOptionValues.parseBookEntityNameOption(" ", "--entity-name"));

    assertEquals("--entity-name", exception.argument());
  }

  @Test
  void parseFiscalYearStartOption_rejectsInvalidMonthDayTokens() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliOptionValues.parseFiscalYearStartOption("13-40", "--fiscal-year-start"));

    assertEquals("--fiscal-year-start", exception.argument());
  }
}
