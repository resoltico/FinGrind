package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.BookAccess;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliArguments}. */
class CliRekeyArgumentParsingTest extends CliArgumentParsingTestSupport {

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
}
