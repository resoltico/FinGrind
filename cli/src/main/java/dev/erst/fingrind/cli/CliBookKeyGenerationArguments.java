package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses CLI arguments for `generate-book-key-file`. */
final class CliBookKeyGenerationArguments {
  private static final List<String> GENERATE_BOOK_KEY_FILE_OPTIONS =
      List.of(
          ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
          ProtocolOptions.BookDefinition.TIGHTEN_PARENTS,
          ProtocolOptions.Presentation.OUTPUT);

  private CliBookKeyGenerationArguments() {}

  static CliCommand parseGenerateBookKeyFileCommand(List<String> arguments) {
    Path bookKeyFilePath = null;
    @Nullable OutputMode outputMode = null;
    boolean tightenParents = false;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE -> {
          if (bookKeyFilePath != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
                "Duplicate argument: " + ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
          }
          bookKeyFilePath =
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
        case ProtocolOptions.BookDefinition.TIGHTEN_PARENTS -> tightenParents = true;
        default ->
            throw CliArgumentValueParser.unsupportedArgument(
                argument, GENERATE_BOOK_KEY_FILE_OPTIONS);
      }
    }
    if (bookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
          "A " + ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE + " argument is required.");
    }
    return new GenerateBookKeyFile(
        bookKeyFilePath, tightenParents, CliOptionModes.resolvedOutputMode(outputMode));
  }
}
