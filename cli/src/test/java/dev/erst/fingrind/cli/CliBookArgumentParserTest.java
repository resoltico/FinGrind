package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliBookArgumentParser}. */
class CliBookArgumentParserTest extends CliArgumentParsingTestSupport {

  @Test
  void commandArgumentSpec_copiesValueAndFlagOptions() {
    List<String> valueOptions = new ArrayList<>(List.of("--output"));
    List<String> flagOptions = new ArrayList<>(List.of("--verbose"));
    CliBookArgumentParser.CommandArgumentSpec spec =
        new CliBookArgumentParser.CommandArgumentSpec(valueOptions, flagOptions);

    valueOptions.add("--cursor");
    flagOptions.add("--dry-run");

    assertEquals(List.of("--output"), spec.valueOptions());
    assertEquals(List.of("--verbose"), spec.flagOptions());
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
}
