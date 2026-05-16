package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliBookArgumentParser}. */
class CliBookArgumentParserTest extends CliArgumentParsingTestSupport {

  @Test
  void commandArgumentSpec_copiesAndFreezesOptions() {
    CliBookArgumentParser.CommandArgumentSpec spec =
        new CliBookArgumentParser.CommandArgumentSpec(
            Map.of(
                "--output", CliBookArgumentParser.OptionArity.VALUE,
                "--verbose", CliBookArgumentParser.OptionArity.FLAG));

    assertEquals(
        Map.of(
            "--output", CliBookArgumentParser.OptionArity.VALUE,
            "--verbose", CliBookArgumentParser.OptionArity.FLAG),
        spec.options());
    assertThrows(
        UnsupportedOperationException.class,
        () -> spec.options().put("--cursor", CliBookArgumentParser.OptionArity.VALUE));
  }

  @Test
  void commandArgumentSpec_distinguishesValueAndFlagOptions() {
    CliBookArgumentParser.CommandArgumentSpec spec =
        CliBookArgumentParser.commandArgumentSpec(List.of("--output"), List.of("--verbose"));

    assertTrue(spec.supports("--output"));
    assertTrue(spec.supports("--verbose"));
    assertFalse(spec.supports("--missing"));
    assertTrue(spec.requiresValue("--output"));
    assertFalse(spec.requiresValue("--verbose"));
  }

  @Test
  void commandArgumentSpec_rejects_duplicate_or_overlapping_options() {
    IllegalArgumentException duplicateValueOption =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliBookArgumentParser.commandArgumentSpec(
                    List.of("--output", "--output"), List.of()));
    IllegalArgumentException overlappingOption =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliBookArgumentParser.commandArgumentSpec(
                    List.of("--output"), List.of("--output")));

    assertEquals(
        "Command argument options must not repeat or overlap: --output",
        duplicateValueOption.getMessage());
    assertEquals(
        "Command argument options must not repeat or overlap: --output",
        overlappingOption.getMessage());
  }

  @Test
  void parseBookAndCommandArguments_collectsFlagArgumentsWithoutValues() {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(
            List.of(
                "open-book",
                "--book-file",
                "book.sqlite",
                "--book-key-file",
                "book.key",
                "--verbose"),
            CliBookArgumentParser.commandArgumentSpec(List.of(), List.of("--verbose")));

    assertEquals(Path.of("book.sqlite"), parsedArguments.bookAccess().bookFilePath());
    assertEquals(List.of("--verbose"), parsedArguments.commandArguments());
  }

  @Test
  void parseRequestBoundArguments_acceptsRequestFileAndRejectsCommandStyleArguments() {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseRequestBoundArguments(
            List.of(
                "post-entry",
                "--book-file",
                "book.sqlite",
                "--book-key-file",
                "book.key",
                "--request-file",
                "request.json"));

    assertEquals(Path.of("book.sqlite"), parsedArguments.bookAccess().bookFilePath());
    assertEquals(Path.of("request.json"), parsedArguments.optionalRequestFile().orElseThrow());
    assertEquals(List.of(), parsedArguments.commandArguments());

    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliBookArgumentParser.parseRequestBoundArguments(
                    List.of(
                        "post-entry",
                        "--book-file",
                        "book.sqlite",
                        "--book-key-file",
                        "book.key",
                        "--output",
                        "json")));

    assertEquals("--output", exception.argument());
    assertEquals("Unsupported argument: --output", exception.getMessage());
  }
}
