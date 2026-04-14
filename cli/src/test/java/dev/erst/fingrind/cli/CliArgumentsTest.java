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
  void parse_returnsGenerateBookKeyFileForValidCommand() {
    CliCommand.GenerateBookKeyFile command =
        assertInstanceOf(
            CliCommand.GenerateBookKeyFile.class,
            CliArguments.parse(
                new String[] {
                  "generate-book-key-file", "--book-key-file", "books/entity.book-key"
                }));

    assertEquals(Path.of("books/entity.book-key"), command.bookKeyFilePath());
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
  void parse_rejectsMissingBookKeyFileForGeneratorCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"generate-book-key-file"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-key-file", exception.argument());
    assertEquals("A --book-key-file argument is required.", exception.getMessage());
  }

  @Test
  void parse_rejectsDuplicateBookKeyFileForGeneratorCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "generate-book-key-file",
                      "--book-key-file",
                      "first.key",
                      "--book-key-file",
                      "second.key"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-key-file", exception.argument());
    assertEquals("Duplicate argument: --book-key-file", exception.getMessage());
  }

  @Test
  void parse_rejectsUnsupportedArgumentForGeneratorCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "generate-book-key-file", "--book-key-file", "entity.key", "--extra"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--extra", exception.argument());
    assertEquals("Unsupported argument: --extra", exception.getMessage());
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
  void parse_returnsRekeyBookForKeyFilePassphraseSources() {
    CliCommand.RekeyBook command =
        assertInstanceOf(
            CliCommand.RekeyBook.class,
            CliArguments.parse(
                new String[] {
                  "rekey-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "current.key",
                  "--new-book-key-file",
                  "replacement.key"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(
        Path.of("current.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(
        Path.of("replacement.key"),
        assertInstanceOf(
                BookAccess.PassphraseSource.KeyFile.class, command.replacementPassphraseSource())
            .bookKeyFilePath());
  }

  @Test
  void parse_returnsRekeyBookForStandardInputAndPromptPassphraseSources() {
    CliCommand.RekeyBook currentStandardInputCommand =
        assertInstanceOf(
            CliCommand.RekeyBook.class,
            CliArguments.parse(
                new String[] {
                  "rekey-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-passphrase-stdin",
                  "--new-book-passphrase-prompt"
                }));
    assertEquals(
        BookAccess.PassphraseSource.StandardInput.INSTANCE,
        currentStandardInputCommand.bookAccess().passphraseSource());
    assertEquals(
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
        currentStandardInputCommand.replacementPassphraseSource());

    CliCommand.RekeyBook currentPromptCommand =
        assertInstanceOf(
            CliCommand.RekeyBook.class,
            CliArguments.parse(
                new String[] {
                  "rekey-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-passphrase-prompt",
                  "--new-book-passphrase-stdin"
                }));
    assertEquals(
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
        currentPromptCommand.bookAccess().passphraseSource());
    assertEquals(
        BookAccess.PassphraseSource.StandardInput.INSTANCE,
        currentPromptCommand.replacementPassphraseSource());
  }

  @Test
  void parse_returnsRekeyBookForKeyFileAndPromptPassphraseSources() {
    CliCommand.RekeyBook command =
        assertInstanceOf(
            CliCommand.RekeyBook.class,
            CliArguments.parse(
                new String[] {
                  "rekey-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "current.key",
                  "--new-book-passphrase-prompt"
                }));

    assertEquals(
        Path.of("current.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
        command.replacementPassphraseSource());
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
  void parse_rejectsDuplicateBookFileArgumentForRekeyCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book-a.sqlite",
                      "--book-key-file",
                      "current.key",
                      "--new-book-key-file",
                      "replacement.key",
                      "--book-file",
                      "book-b.sqlite"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-file", exception.argument());
    assertEquals("Duplicate argument: --book-file", exception.getMessage());
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
  void parse_rejectsRekeyCommandWithoutCurrentPassphraseSource() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--new-book-key-file",
                      "replacement.key"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "Exactly one current book passphrase source is required: --book-key-file <path>,"
            + " --book-passphrase-stdin, or --book-passphrase-prompt.",
        exception.getMessage());
  }

  @Test
  void parse_rejectsRekeyCommandWithoutBookFile() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-key-file",
                      "current.key",
                      "--new-book-key-file",
                      "next.key"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-file", exception.argument());
    assertEquals("A --book-file argument is required.", exception.getMessage());
  }

  @Test
  void parse_rejectsRekeyCommandWithoutReplacementPassphraseSource() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book", "--book-file", "book.sqlite", "--book-key-file", "current.key"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--new-book-key-file", exception.argument());
    assertEquals(
        "Exactly one replacement book passphrase source is required: --new-book-key-file <path>,"
            + " --new-book-passphrase-stdin, or --new-book-passphrase-prompt.",
        exception.getMessage());
  }

  @Test
  void parse_rejectsDuplicateReplacementRekeyPassphraseSources() {
    CliArgumentsException duplicateKeyFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "current.key",
                      "--new-book-key-file",
                      "replacement-a.key",
                      "--new-book-key-file",
                      "replacement-b.key"
                    }));
    assertEquals("--new-book-key-file", duplicateKeyFile.argument());
    assertEquals(
        "Exactly one replacement book passphrase source is permitted per command.",
        duplicateKeyFile.getMessage());

    CliArgumentsException duplicateStandardInput =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "current.key",
                      "--new-book-passphrase-stdin",
                      "--new-book-passphrase-stdin"
                    }));
    assertEquals("--new-book-passphrase-stdin", duplicateStandardInput.argument());

    CliArgumentsException duplicatePrompt =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "current.key",
                      "--new-book-passphrase-prompt",
                      "--new-book-passphrase-prompt"
                    }));
    assertEquals("--new-book-passphrase-prompt", duplicatePrompt.argument());
  }

  @Test
  void parse_rejectsRekeyCommandWithSameCurrentAndReplacementKeyFilePaths() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "shared.key",
                      "--new-book-key-file",
                      "shared.key"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--new-book-key-file", exception.argument());
    assertEquals(
        "--book-key-file and --new-book-key-file must not point to the same path.",
        exception.getMessage());
  }

  @Test
  void parse_rejectsUnsupportedRekeyArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "current.key",
                      "--new-book-key-file",
                      "next.key",
                      "--wat"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--wat", exception.argument());
    assertEquals("Unsupported argument: --wat", exception.getMessage());
  }

  @Test
  void parse_rejectsUsingStandardInputForBothCurrentAndReplacementRekeyPassphrases() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-passphrase-stdin",
                      "--new-book-passphrase-stdin"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--new-book-passphrase-stdin", exception.argument());
    assertEquals(
        "Standard input cannot supply both the current and replacement book passphrases.",
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
