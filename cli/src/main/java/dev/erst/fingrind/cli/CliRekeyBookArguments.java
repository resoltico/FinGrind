package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses CLI arguments for `rekey-book`. */
final class CliRekeyBookArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec REKEY_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE, ProtocolOptions.Presentation.OUTPUT),
          List.of());

  private CliRekeyBookArguments() {}

  static CliCommand parseRekeyBookCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, REKEY_BOOK_ARGUMENTS);
    CliBookArgumentParser.requireAttestationCredentials(parsedArguments.bookAccess());
    Path newBookKeyFilePath = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE -> {
          if (newBookKeyFilePath != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
                "Duplicate argument: " + ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
          }
          newBookKeyFilePath =
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
        }
        case ProtocolOptions.Presentation.OUTPUT ->
            outputMode =
                CliOptionModes.requireOutputMode(
                    outputMode,
                    CliOptionValues.requireValue(
                        argumentIterator, ProtocolOptions.Presentation.OUTPUT),
                    CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
        default -> throw CliArgumentValueParser.unsupportedArgument(argument, List.of());
      }
    }
    if (newBookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
          "A " + ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE + " argument is required.");
    }
    CliBookPathValidator.validateDistinctRekeyTarget(
        parsedArguments.bookAccess().bookFilePath(),
        parsedArguments.bookAccess().passphraseSource(),
        newBookKeyFilePath);
    return new RekeyBook(
        parsedArguments.bookAccess(),
        newBookKeyFilePath,
        CliOptionModes.resolvedOutputMode(outputMode));
  }
}
