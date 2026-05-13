package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses discovery-style CLI commands that do not target a selected book. */
final class CliDiscoveryArguments {
  private CliDiscoveryArguments() {}

  static CliCommand parseHelp(List<String> arguments) {
    @Nullable OperationId commandTopic = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode =
            CliArgumentValueParser.requireOutputMode(
                outputMode,
                CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
        continue;
      }
      if (commandTopic != null) {
        throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
      commandTopic = requiredCommandTopic(argument);
    }
    return new Help(commandTopic, CliArgumentValueParser.resolvedDiscoveryOutputMode(outputMode));
  }

  static CliCommand parseCommandHelp(OperationId commandTopic, List<String> arguments) {
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(2);
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
    return new Help(commandTopic, CliArgumentValueParser.resolvedDiscoveryOutputMode(outputMode));
  }

  static CliCommand parseVersion(List<String> arguments) {
    return parseDiscoveryCommand(arguments, Version::new);
  }

  static CliCommand parseCapabilities(List<String> arguments) {
    return parseDiscoveryCommand(arguments, Capabilities::new);
  }

  static CliCommand parsePrintRequestTemplate(List<String> arguments) {
    return parseSingleToken(arguments, new PrintRequestTemplate());
  }

  static CliCommand parsePrintPlanTemplate(List<String> arguments) {
    return parseSingleToken(arguments, new PrintPlanTemplate());
  }

  private static CliCommand parseSingleToken(List<String> arguments, CliCommand command) {
    if (arguments.size() != 1) {
      String unsupportedArgument = arguments.get(1);
      throw CliArgumentValueParser.invalid(
          unsupportedArgument,
          "%s emits fixed raw JSON and does not accept %s. Use shell redirection if you need to save the template."
              .formatted(arguments.getFirst(), unsupportedArgument));
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

  private static OperationId requiredCommandTopic(String token) {
    Optional<dev.erst.fingrind.contract.protocol.ProtocolOperation> operation =
        ProtocolCatalog.findByToken(token);
    if (operation.isEmpty()) {
      throw CliArgumentValueParser.invalid(token, "Unsupported help topic: " + token);
    }
    return operation.orElseThrow().id();
  }

  /** Factory for one discovery command that only varies by the selected output mode. */
  @FunctionalInterface
  private interface DiscoveryCommandFactory {
    /** Builds one parsed discovery command with the resolved output mode. */
    CliCommand create(OutputMode outputMode);
  }
}
