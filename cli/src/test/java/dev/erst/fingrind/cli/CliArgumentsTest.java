package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliArguments}. */
class CliArgumentsTest {
  @Test
  void parse_returnsHelpWhenArgumentsAreEmpty() {
    assertInstanceOf(CliCommand.Help.class, CliArguments.parse(new String[0]));
  }

  @Test
  void parse_returnsCapabilitiesWhenCommandIsCapabilities() {
    assertInstanceOf(
        CliCommand.Capabilities.class, CliArguments.parse(new String[] {"capabilities"}));
  }

  @Test
  void parse_returnsHelpForFlagAlias() {
    assertInstanceOf(CliCommand.Help.class, CliArguments.parse(new String[] {"--help"}));
  }

  @Test
  void parse_returnsVersionForFlagAlias() {
    assertInstanceOf(CliCommand.Version.class, CliArguments.parse(new String[] {"--version"}));
  }

  @Test
  void parse_returnsPrintRequestTemplateForCommand() {
    assertInstanceOf(
        CliCommand.PrintRequestTemplate.class,
        CliArguments.parse(new String[] {"print-request-template"}));
  }

  @Test
  void parse_returnsPreflightEntryForValidEntryCommand() {
    CliCommand.PreflightEntry command =
        assertInstanceOf(
            CliCommand.PreflightEntry.class,
            CliArguments.parse(
                new String[] {
                  "preflight-entry", "--book-file", "book.sqlite", "--request-file", "request.json"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookFilePath());
    assertEquals(Path.of("request.json"), command.requestFile());
  }

  @Test
  void parse_returnsPostEntryForValidEntryCommand() {
    CliCommand.PostEntry command =
        assertInstanceOf(
            CliCommand.PostEntry.class,
            CliArguments.parse(
                new String[] {
                  "post-entry", "--book-file", "book.sqlite", "--request-file", "request.json"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookFilePath());
    assertEquals(Path.of("request.json"), command.requestFile());
  }

  @Test
  void parse_rejectsDuplicateBookFileArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book-a.sqlite",
                      "--book-file",
                      "book-b.sqlite",
                      "--request-file",
                      "request.json"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-file", exception.argument());
    assertEquals("Duplicate argument: --book-file", exception.getMessage());
    assertEquals("Run 'fingrind help' to inspect the supported command syntax.", exception.hint());
  }

  @Test
  void parse_rejectsDuplicateRequestFileArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--request-file",
                      "request-a.json",
                      "--request-file",
                      "request-b.json"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--request-file", exception.argument());
    assertEquals("Duplicate argument: --request-file", exception.getMessage());
  }

  @Test
  void parse_rejectsSameBookAndRequestPath() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry", "--book-file", "shared.path", "--request-file", "shared.path"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--request-file", exception.argument());
    assertEquals(
        "--book-file and --request-file must not point to the same path.", exception.getMessage());
  }

  @Test
  void parse_rejectsUnknownCommand() {
    CliArgumentsException exception =
        assertThrows(CliArgumentsException.class, () -> CliArguments.parse(new String[] {"wat"}));

    assertEquals("unknown-command", exception.code());
    assertEquals("wat", exception.argument());
    assertEquals("Unsupported command: wat", exception.getMessage());
    assertEquals(
        "Run 'fingrind help' to inspect the supported commands and examples.", exception.hint());
  }
}
