package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses CLI arguments for `rekey-book`. */
final class CliRekeyBookArguments {
  private CliRekeyBookArguments() {}

  static CliCommand parseRekeyBookCommand(List<String> arguments) {
    RekeyBookArgumentValues argumentValues = new RekeyBookArgumentValues();
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      applyRekeyBookArgument(argumentValues, argumentIterator.next(), argumentIterator);
    }
    if (argumentValues.bookFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_FILE, "A " + ProtocolOptions.BOOK_FILE + " argument is required.");
    }
    if (argumentValues.currentPassphraseSourceKind == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "Exactly one current book passphrase source is required: "
              + ProtocolOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    if (argumentValues.replacementPassphraseSourceKind == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.REPLACEMENT_BOOK_KEY_FILE,
          "Exactly one replacement book passphrase source is required: "
              + ProtocolOptions.REPLACEMENT_BOOK_KEY_FILE
              + " <existing-path>, "
              + ProtocolOptions.REPLACEMENT_BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.REPLACEMENT_BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    BookAccess.PassphraseSource currentPassphraseSource =
        CliBookPassphraseParser.passphraseSource(
            argumentValues.currentPassphraseSourceKind, argumentValues.currentBookKeyFilePath);
    BookAccess.PassphraseSource replacementPassphraseSource =
        CliBookPassphraseParser.passphraseSource(
            argumentValues.replacementPassphraseSourceKind,
            argumentValues.replacementBookKeyFilePath);
    CliBookPathValidator.validateDistinctRekeyPaths(
        argumentValues.bookFilePath, currentPassphraseSource, replacementPassphraseSource);
    CliBookPathValidator.validateRekeyStandardInputUsage(
        currentPassphraseSource, replacementPassphraseSource);
    return new RekeyBook(
        new BookAccess(argumentValues.bookFilePath, currentPassphraseSource),
        replacementPassphraseSource,
        CliOptionModes.resolvedOutputMode(argumentValues.outputMode));
  }

  private static void applyRekeyBookArgument(
      RekeyBookArgumentValues argumentValues,
      String argument,
      ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolOptions.BOOK_FILE ->
          argumentValues.bookFilePath =
              requireSingleRekeyPath(
                  argumentValues.bookFilePath, argumentIterator, ProtocolOptions.BOOK_FILE);
      case ProtocolOptions.BOOK_KEY_FILE -> {
        argumentValues.currentPassphraseSourceKind =
            CliBookPassphraseParser.requireSinglePassphraseSource(
                argumentValues.currentPassphraseSourceKind,
                CliBookPassphraseParser.PassphraseSourceKind.KEY_FILE);
        argumentValues.currentBookKeyFilePath =
            CliOptionValues.requirePathOptionValue(argumentIterator, ProtocolOptions.BOOK_KEY_FILE);
      }
      case ProtocolOptions.BOOK_PASSPHRASE_STDIN ->
          argumentValues.currentPassphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  argumentValues.currentPassphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.STANDARD_INPUT);
      case ProtocolOptions.BOOK_PASSPHRASE_PROMPT ->
          argumentValues.currentPassphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  argumentValues.currentPassphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.INTERACTIVE_PROMPT);
      case ProtocolOptions.REPLACEMENT_BOOK_KEY_FILE -> {
        argumentValues.replacementPassphraseSourceKind =
            CliBookPassphraseParser.requireSingleReplacementPassphraseSource(
                argumentValues.replacementPassphraseSourceKind,
                CliBookPassphraseParser.PassphraseSourceKind.KEY_FILE);
        argumentValues.replacementBookKeyFilePath =
            CliOptionValues.requirePathOptionValue(
                argumentIterator, ProtocolOptions.REPLACEMENT_BOOK_KEY_FILE);
      }
      case ProtocolOptions.REPLACEMENT_BOOK_PASSPHRASE_STDIN ->
          argumentValues.replacementPassphraseSourceKind =
              CliBookPassphraseParser.requireSingleReplacementPassphraseSource(
                  argumentValues.replacementPassphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.STANDARD_INPUT);
      case ProtocolOptions.REPLACEMENT_BOOK_PASSPHRASE_PROMPT ->
          argumentValues.replacementPassphraseSourceKind =
              CliBookPassphraseParser.requireSingleReplacementPassphraseSource(
                  argumentValues.replacementPassphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.INTERACTIVE_PROMPT);
      case ProtocolOptions.OUTPUT ->
          argumentValues.outputMode =
              CliOptionModes.requireOutputMode(
                  argumentValues.outputMode,
                  CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                  CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      default ->
          throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
    }
  }

  private static Path requireSingleRekeyPath(
      @Nullable Path currentPath, ListIterator<String> argumentIterator, String optionName) {
    if (currentPath != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.requirePathOptionValue(argumentIterator, optionName);
  }

  /** Mutable parse accumulator for rekey-book command options before validation. */
  private static final class RekeyBookArgumentValues {
    private @Nullable Path bookFilePath;
    private @Nullable Path currentBookKeyFilePath;
    private @Nullable Path replacementBookKeyFilePath;
    private CliBookPassphraseParser.@Nullable PassphraseSourceKind currentPassphraseSourceKind;
    private CliBookPassphraseParser.@Nullable PassphraseSourceKind replacementPassphraseSourceKind;
    private @Nullable OutputMode outputMode;
  }
}
