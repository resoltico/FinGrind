package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses CLI arguments for `generate-book-key-file`. */
final class CliBookKeyGenerationArguments {
  private static final List<String> GENERATE_BOOK_KEY_FILE_OPTIONS =
      List.of(ProtocolOptions.BOOK_KEY_FILE, ProtocolOptions.OUTPUT);

  private CliBookKeyGenerationArguments() {}

  static CliCommand parseGenerateBookKeyFileCommand(List<String> arguments) {
    Path bookKeyFilePath = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.BOOK_KEY_FILE -> {
          if (bookKeyFilePath != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.BOOK_KEY_FILE,
                "Duplicate argument: " + ProtocolOptions.BOOK_KEY_FILE);
          }
          bookKeyFilePath =
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_KEY_FILE);
        }
        case ProtocolOptions.OUTPUT ->
            outputMode =
                CliOptionModes.requireOutputMode(
                    outputMode,
                    CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                    CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
        default ->
            throw CliArgumentValueParser.unsupportedArgument(
                argument, GENERATE_BOOK_KEY_FILE_OPTIONS);
      }
    }
    if (bookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "A " + ProtocolOptions.BOOK_KEY_FILE + " argument is required.");
    }
    return new GenerateBookKeyFile(bookKeyFilePath, CliOptionModes.resolvedOutputMode(outputMode));
  }
}
