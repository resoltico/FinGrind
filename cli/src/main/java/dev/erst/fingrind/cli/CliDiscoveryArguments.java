package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses discovery-style CLI commands that do not target a selected book. */
final class CliDiscoveryArguments {
  private CliDiscoveryArguments() {}

  static CliCommand parseHelp(List<String> arguments) {
    return parseDiscoveryCommand(arguments, CliCommand.Help::new);
  }

  static CliCommand parseVersion(List<String> arguments) {
    return parseDiscoveryCommand(arguments, CliCommand.Version::new);
  }

  static CliCommand parseCapabilities(List<String> arguments) {
    return parseDiscoveryCommand(arguments, CliCommand.Capabilities::new);
  }

  static CliCommand parsePrintRequestTemplate(List<String> arguments) {
    return parseSingleToken(arguments, new CliCommand.PrintRequestTemplate());
  }

  static CliCommand parsePrintPlanTemplate(List<String> arguments) {
    return parseSingleToken(arguments, new CliCommand.PrintPlanTemplate());
  }

  private static CliCommand parseSingleToken(List<String> arguments, CliCommand command) {
    if (arguments.size() != 1) {
      throw CliArgumentValueParser.invalid(
          arguments.get(1), "This command does not accept additional arguments.");
    }
    return command;
  }

  private static CliCommand parseDiscoveryCommand(
      List<String> arguments, DiscoveryCommandFactory commandFactory) {
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (!ProtocolOptions.OUTPUT.equals(argument)) {
        throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
      outputMode =
          CliArgumentValueParser.requireOutputMode(
              outputMode,
              CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
    }
    return commandFactory.create(CliArgumentValueParser.resolvedDiscoveryOutputMode(outputMode));
  }

  /** Factory for one discovery command that only varies by the selected output mode. */
  @FunctionalInterface
  private interface DiscoveryCommandFactory {
    /** Builds one parsed discovery command with the resolved output mode. */
    CliCommand create(OutputMode outputMode);
  }
}
