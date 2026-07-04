package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared parser support for close commands with one required option plus output mode. */
final class CliCloseCommandArgumentSupport {
  private CliCloseCommandArgumentSupport() {}

  /** Parses the one required close-command option from its raw string value. */
  @FunctionalInterface
  interface RequiredOptionParser<T> {
    /** Converts one raw option value into the caller-owned typed argument. */
    T parse(String value, String optionName);
  }

  /** Parses one close command that accepts one required option plus the shared output selector. */
  static <T> ParsedCloseArgument<T> parseSingleRequiredOption(
      List<String> commandArguments,
      String requiredOption,
      List<String> supportedOptions,
      RequiredOptionParser<T> parser) {
    Objects.requireNonNull(commandArguments, "commandArguments");
    Objects.requireNonNull(requiredOption, "requiredOption");
    Objects.requireNonNull(supportedOptions, "supportedOptions");
    Objects.requireNonNull(parser, "parser");
    @Nullable T requiredValue = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = commandArguments.listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (requiredOption.equals(argument)) {
        if (requiredValue != null) {
          throw CliArgumentValueParser.invalid(
              requiredOption, "Duplicate argument: " + requiredOption);
        }
        requiredValue =
            parser.parse(
                CliOptionValues.requireValue(argumentIterator, requiredOption), requiredOption);
        continue;
      }
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
        continue;
      }
      throw CliArgumentValueParser.unsupportedArgument(argument, supportedOptions);
    }
    if (requiredValue == null) {
      throw CliArgumentValueParser.invalid(
          requiredOption, "A " + requiredOption + " argument is required.");
    }
    return new ParsedCloseArgument<>(requiredValue, outputMode);
  }

  /** Parsed close-command arguments with the required typed value and optional output mode. */
  record ParsedCloseArgument<T>(T requiredValue, @Nullable OutputMode outputMode) {}
}
