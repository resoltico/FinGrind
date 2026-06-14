package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused unit tests for CLI argument-value parser diagnostics. */
class CliArgumentValueParserTest {
  @Test
  void unsupportedArgument_suggestsNearestOptionAcrossEditAndPrefixCases() {
    assertEquals(
        "Unsupported argument: --outpt. Did you mean --output?",
        CliArgumentValueParser.unsupportedArgument(
                "--outpt", List.of("--output", "--detail", "--pdf-out"))
            .getMessage());
    assertEquals(
        "Unsupported argument: --output-extra. Did you mean --output?",
        CliArgumentValueParser.unsupportedArgument("--output-extra", List.of("--output"))
            .getMessage());
    assertEquals(
        "Unsupported argument: positional-token",
        CliArgumentValueParser.unsupportedArgument("positional-token", List.of("--output"))
            .getMessage());
    assertEquals(
        "Unsupported argument: --z",
        CliArgumentValueParser.unsupportedArgument("--z", List.of("--output", "--detail"))
            .getMessage());
    assertEquals(
        "Unsupported argument: --outpu. Did you mean --output?",
        CliArgumentValueParser.unsupportedArgument(
                "--outpu", List.of("--output", "--outline", "--outbox"))
            .getMessage());
    assertEquals(
        "Unsupported argument: --o. Did you mean --output?",
        CliArgumentValueParser.unsupportedArgument("--o", List.of("--outline", "--output"))
            .getMessage());
  }

  @Test
  void invalidEnvironmentSelection_recommendsAcceptedValues() {
    CliArgumentsException exception =
        CliArgumentValueParser.invalidEnvironmentSelection(
            "FINGRIND_DEFAULT_OUTPUT",
            "Unsupported configured output.",
            new IllegalArgumentException("bad"));

    assertEquals("FINGRIND_DEFAULT_OUTPUT", exception.argument());
    assertTrue(exception.hint().contains("Unset FINGRIND_DEFAULT_OUTPUT"));
    assertTrue(exception.hint().contains("json, text"));
  }

  @Test
  void requirePageLimit_rejectsOutOfRangeValuesAndAcceptsBoundaries() {
    assertEquals(1, CliArgumentValueParser.requirePageLimit(1, "--limit"));
    assertEquals(200, CliArgumentValueParser.requirePageLimit(200, "--limit"));

    CliArgumentsException low =
        org.junit.jupiter.api.Assertions.assertThrows(
            CliArgumentsException.class,
            () -> CliArgumentValueParser.requirePageLimit(0, "--limit"));
    CliArgumentsException high =
        org.junit.jupiter.api.Assertions.assertThrows(
            CliArgumentsException.class,
            () -> CliArgumentValueParser.requirePageLimit(201, "--limit"));

    assertEquals("--limit", low.argument());
    assertEquals("--limit", high.argument());
  }
}
