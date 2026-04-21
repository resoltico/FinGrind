package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses lifecycle-style mutation commands such as key generation, open-book, and rekey-book. */
final class CliLifecycleMutationArguments {
  private CliLifecycleMutationArguments() {}

  static CliCommand parseGenerateBookKeyFileCommand(List<String> arguments) {
    Path bookKeyFilePath = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.BOOK_KEY_FILE -> {
          if (bookKeyFilePath != null) {
            throw CliArgumentSupport.invalid(
                ProtocolOptions.BOOK_KEY_FILE,
                "Duplicate argument: " + ProtocolOptions.BOOK_KEY_FILE);
          }
          bookKeyFilePath =
              CliArgumentSupport.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_KEY_FILE);
        }
        case ProtocolOptions.OUTPUT ->
            outputMode =
                CliArgumentSupport.requireOutputMode(
                    outputMode,
                    CliArgumentSupport.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                    CliArgumentSupport.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
        default -> throw CliArgumentSupport.invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (bookKeyFilePath == null) {
      throw CliArgumentSupport.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "A " + ProtocolOptions.BOOK_KEY_FILE + " argument is required.");
    }
    return new CliCommand.GenerateBookKeyFile(
        bookKeyFilePath, CliArgumentSupport.resolvedOutputMode(outputMode));
  }

  static CliCommand parseOpenBookCommand(List<String> arguments) {
    CliBookArgumentSupport.ParsedBookArguments parsedArguments =
        CliBookArgumentSupport.parseBookAndCommandArguments(arguments);
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (!ProtocolOptions.OUTPUT.equals(argument)) {
        throw CliArgumentSupport.invalid(argument, "Unsupported argument: " + argument);
      }
      outputMode =
          CliArgumentSupport.requireOutputMode(
              outputMode,
              CliArgumentSupport.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliArgumentSupport.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
    }
    return new CliCommand.OpenBook(
        parsedArguments.bookAccess(), CliArgumentSupport.resolvedOutputMode(outputMode));
  }

  static CliCommand parseRekeyBookCommand(List<String> arguments) {
    Path bookFilePath = null;
    Path currentBookKeyFilePath = null;
    Path replacementBookKeyFilePath = null;
    CliBookPassphraseArgumentSupport.PassphraseSourceKind currentPassphraseSourceKind = null;
    CliBookPassphraseArgumentSupport.PassphraseSourceKind replacementPassphraseSourceKind = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.BOOK_FILE -> {
          if (bookFilePath != null) {
            throw CliArgumentSupport.invalid(
                ProtocolOptions.BOOK_FILE, "Duplicate argument: " + ProtocolOptions.BOOK_FILE);
          }
          bookFilePath =
              CliArgumentSupport.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_FILE);
        }
        case ProtocolOptions.BOOK_KEY_FILE -> {
          currentPassphraseSourceKind =
              CliBookPassphraseArgumentSupport.requireSinglePassphraseSource(
                  currentPassphraseSourceKind,
                  CliBookPassphraseArgumentSupport.PassphraseSourceKind.KEY_FILE);
          currentBookKeyFilePath =
              CliArgumentSupport.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_KEY_FILE);
        }
        case ProtocolOptions.BOOK_PASSPHRASE_STDIN -> {
          currentPassphraseSourceKind =
              CliBookPassphraseArgumentSupport.requireSinglePassphraseSource(
                  currentPassphraseSourceKind,
                  CliBookPassphraseArgumentSupport.PassphraseSourceKind.STANDARD_INPUT);
        }
        case ProtocolOptions.BOOK_PASSPHRASE_PROMPT -> {
          currentPassphraseSourceKind =
              CliBookPassphraseArgumentSupport.requireSinglePassphraseSource(
                  currentPassphraseSourceKind,
                  CliBookPassphraseArgumentSupport.PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        case ProtocolOptions.NEW_BOOK_KEY_FILE -> {
          replacementPassphraseSourceKind =
              CliBookPassphraseArgumentSupport.requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind,
                  CliBookPassphraseArgumentSupport.PassphraseSourceKind.KEY_FILE);
          replacementBookKeyFilePath =
              CliArgumentSupport.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.NEW_BOOK_KEY_FILE);
        }
        case ProtocolOptions.NEW_BOOK_PASSPHRASE_STDIN -> {
          replacementPassphraseSourceKind =
              CliBookPassphraseArgumentSupport.requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind,
                  CliBookPassphraseArgumentSupport.PassphraseSourceKind.STANDARD_INPUT);
        }
        case ProtocolOptions.NEW_BOOK_PASSPHRASE_PROMPT -> {
          replacementPassphraseSourceKind =
              CliBookPassphraseArgumentSupport.requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind,
                  CliBookPassphraseArgumentSupport.PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        case ProtocolOptions.OUTPUT ->
            outputMode =
                CliArgumentSupport.requireOutputMode(
                    outputMode,
                    CliArgumentSupport.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                    CliArgumentSupport.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
        default -> throw CliArgumentSupport.invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (bookFilePath == null) {
      throw CliArgumentSupport.invalid(
          ProtocolOptions.BOOK_FILE, "A " + ProtocolOptions.BOOK_FILE + " argument is required.");
    }
    if (currentPassphraseSourceKind == null) {
      throw CliArgumentSupport.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "Exactly one current book passphrase source is required: "
              + ProtocolOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    if (replacementPassphraseSourceKind == null) {
      throw CliArgumentSupport.invalid(
          ProtocolOptions.NEW_BOOK_KEY_FILE,
          "Exactly one replacement book passphrase source is required: "
              + ProtocolOptions.NEW_BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.NEW_BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.NEW_BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    BookAccess.PassphraseSource currentPassphraseSource =
        CliBookPassphraseArgumentSupport.passphraseSource(
            currentPassphraseSourceKind, currentBookKeyFilePath);
    BookAccess.PassphraseSource replacementPassphraseSource =
        CliBookPassphraseArgumentSupport.passphraseSource(
            replacementPassphraseSourceKind, replacementBookKeyFilePath);
    CliBookPathValidationSupport.validateDistinctRekeyPaths(
        bookFilePath, currentPassphraseSource, replacementPassphraseSource);
    CliBookPathValidationSupport.validateRekeyStandardInputUsage(
        currentPassphraseSource, replacementPassphraseSource);
    return new CliCommand.RekeyBook(
        new BookAccess(bookFilePath, currentPassphraseSource),
        replacementPassphraseSource,
        CliArgumentSupport.resolvedOutputMode(outputMode));
  }
}
