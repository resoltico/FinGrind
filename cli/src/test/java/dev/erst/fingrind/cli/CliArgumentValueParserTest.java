package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for low-level CLI argument helper branches. */
class CliArgumentValueParserTest {
  @Test
  void requirePageLimit_acceptsBoundaryValuesAndRejectsOutOfRangeValues() {
    assertEquals(1, CliArgumentValueParser.requirePageLimit(1, "--limit"));
    assertEquals(200, CliArgumentValueParser.requirePageLimit(200, "--limit"));

    CliArgumentsException belowMinimum =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArgumentValueParser.requirePageLimit(0, "--limit"));
    CliArgumentsException aboveMaximum =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArgumentValueParser.requirePageLimit(201, "--limit"));

    assertEquals("--limit", belowMinimum.argument());
    assertEquals("--limit", aboveMaximum.argument());
  }
}
