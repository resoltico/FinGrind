package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.application.BookAccess;
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
  void parse_returnsOpenBookForValidBookOnlyCommand() {
    CliCommand.OpenBook command =
        assertInstanceOf(
            CliCommand.OpenBook.class,
            CliArguments.parse(
                new String[] {
                  "open-book", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
  }

  @Test
  void parse_returnsDeclareAccountForValidRequestBoundCommand() {
    CliCommand.DeclareAccount command =
        assertInstanceOf(
            CliCommand.DeclareAccount.class,
            CliArguments.parse(
                new String[] {
                  "declare-account",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "account.json"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(Path.of("account.json"), command.requestFile());
  }

  @Test
  void parse_returnsListAccountsForValidBookOnlyCommand() {
    CliCommand.ListAccounts command =
        assertInstanceOf(
            CliCommand.ListAccounts.class,
            CliArguments.parse(
                new String[] {
                  "list-accounts", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
  }

  @Test
  void parse_rejectsAdditionalArgumentForSingleTokenCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"capabilities", "--extra"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--extra", exception.argument());
    assertEquals("This command does not accept additional arguments.", exception.getMessage());
  }

  @Test
  void parse_returnsPreflightEntryForValidEntryCommand() {
    CliCommand.PreflightEntry command =
        assertInstanceOf(
            CliCommand.PreflightEntry.class,
            CliArguments.parse(
                new String[] {
                  "preflight-entry",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "request.json"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(Path.of("request.json"), command.requestFile());
  }

  @Test
  void parse_returnsPostEntryForValidEntryCommand() {
    CliCommand.PostEntry command =
        assertInstanceOf(
            CliCommand.PostEntry.class,
            CliArguments.parse(
                new String[] {
                  "post-entry",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "request.json"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(Path.of("request.json"), command.requestFile());
  }

  @Test
  void parse_returnsOpenBookForStandardInputPassphraseSource() {
    CliCommand.OpenBook command =
        assertInstanceOf(
            CliCommand.OpenBook.class,
            CliArguments.parse(
                new String[] {
                  "open-book", "--book-file", "book.sqlite", "--book-passphrase-stdin"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(
        BookAccess.PassphraseSource.StandardInput.INSTANCE,
        command.bookAccess().passphraseSource());
  }

  @Test
  void parse_returnsOpenBookForInteractivePromptPassphraseSource() {
    CliCommand.OpenBook command =
        assertInstanceOf(
            CliCommand.OpenBook.class,
            CliArguments.parse(
                new String[] {
                  "open-book", "--book-file", "book.sqlite", "--book-passphrase-prompt"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
        command.bookAccess().passphraseSource());
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
                      "--book-key-file",
                      "book.key",
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
                      "--book-key-file",
                      "book.key",
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
  void parse_rejectsDuplicateBookKeyFileArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "key-a.key",
                      "--book-key-file",
                      "key-b.key",
                      "--request-file",
                      "request.json"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "Exactly one book passphrase source is permitted per command.", exception.getMessage());
  }

  @Test
  void parse_rejectsMixedBookPassphraseSources() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--book-passphrase-prompt",
                      "--request-file",
                      "request.json"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-passphrase-prompt", exception.argument());
    assertEquals(
        "Exactly one book passphrase source is permitted per command.", exception.getMessage());
  }

  @Test
  void parse_rejectsUnsupportedEntryArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--request-file",
                      "request.json",
                      "--wat"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--wat", exception.argument());
    assertEquals("Unsupported argument: --wat", exception.getMessage());
  }

  @Test
  void parse_rejectsMissingBookFileValue() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"post-entry", "--book-file"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-file", exception.argument());
    assertEquals("Missing value for --book-file.", exception.getMessage());
  }

  @Test
  void parse_rejectsMissingRequestFileArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--request-file", exception.argument());
    assertEquals("A --request-file argument is required.", exception.getMessage());
  }

  @Test
  void parse_rejectsMissingBookKeyFileArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"post-entry", "--book-file", "book.sqlite"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "Exactly one book passphrase source is required: --book-key-file <path>,"
            + " --book-passphrase-stdin, or --book-passphrase-prompt.",
        exception.getMessage());
  }

  @Test
  void parse_rejectsUsingStandardInputForBothPassphraseAndRequestJson() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--book-passphrase-stdin",
                      "--request-file",
                      "-"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-passphrase-stdin", exception.argument());
    assertEquals(
        "Standard input cannot supply both the book passphrase and the request JSON.",
        exception.getMessage());
  }

  @Test
  void parse_allowsRequestJsonFromStandardInputWhenPassphraseUsesPrompt() {
    CliCommand.PostEntry command =
        assertInstanceOf(
            CliCommand.PostEntry.class,
            CliArguments.parse(
                new String[] {
                  "post-entry",
                  "--book-file",
                  "book.sqlite",
                  "--book-passphrase-prompt",
                  "--request-file",
                  "-"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
        command.bookAccess().passphraseSource());
    assertEquals(Path.of("-"), command.requestFile());
  }

  @Test
  void parse_rejectsRequestFileOnBookOnlyCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--request-file",
                      "oops.json"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--request-file", exception.argument());
    assertEquals("Unsupported argument: --request-file", exception.getMessage());
  }

  @Test
  void parse_rejectsSameBookAndRequestPath() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "shared.path",
                      "--book-key-file",
                      "book.key",
                      "--request-file",
                      "shared.path"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--request-file", exception.argument());
    assertEquals(
        "--book-file and --request-file must not point to the same path.", exception.getMessage());
  }

  @Test
  void parse_rejectsSameBookKeyAndRequestPath() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "shared.path",
                      "--request-file",
                      "shared.path"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "--book-key-file and --request-file must not point to the same path.",
        exception.getMessage());
  }

  @Test
  void parse_rejectsSameBookAndKeyPath() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book", "--book-file", "shared.path", "--book-key-file", "shared.path"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "--book-file and --book-key-file must not point to the same path.", exception.getMessage());
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

  private static BookAccess.PassphraseSource.KeyFile assertKeyFileSource(BookAccess bookAccess) {
    return assertInstanceOf(
        BookAccess.PassphraseSource.KeyFile.class, bookAccess.passphraseSource());
  }
}
