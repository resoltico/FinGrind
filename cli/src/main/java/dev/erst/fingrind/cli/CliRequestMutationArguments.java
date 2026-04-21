package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/**
 * Parses request-bound mutation commands such as declare-account, execute-plan, and posting flows.
 */
final class CliRequestMutationArguments {
  private CliRequestMutationArguments() {}

  static CliCommand parseDeclareAccountCommand(List<String> arguments) {
    CliBookArgumentSupport.ParsedBookArguments parsedArguments =
        CliBookArgumentSupport.parseRequestBoundCommandArguments(arguments);
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
    return new CliCommand.DeclareAccount(
        parsedArguments.bookAccess(),
        parsedArguments.optionalRequestFile().orElseThrow(),
        CliArgumentSupport.resolvedOutputMode(outputMode));
  }

  static CliCommand parseExecutePlanCommand(List<String> arguments) {
    CliBookArgumentSupport.ParsedBookArguments parsedArguments =
        CliBookArgumentSupport.parseRequestBoundArguments(arguments);
    return new CliCommand.ExecutePlan(
        parsedArguments.bookAccess(), parsedArguments.optionalRequestFile().orElseThrow());
  }

  static CliCommand parsePreflightEntryCommand(List<String> arguments) {
    return parseRequestBoundOutputCommand(arguments, CliCommand.PreflightEntry::new);
  }

  static CliCommand parsePostEntryCommand(List<String> arguments) {
    return parseRequestBoundOutputCommand(arguments, CliCommand.PostEntry::new);
  }

  private static CliCommand parseRequestBoundOutputCommand(
      List<String> arguments, RequestBoundOutputCommandFactory commandFactory) {
    CliBookArgumentSupport.ParsedBookArguments parsedArguments =
        CliBookArgumentSupport.parseRequestBoundCommandArguments(arguments);
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
    return commandFactory.create(
        parsedArguments.bookAccess(),
        parsedArguments.optionalRequestFile().orElseThrow(),
        CliArgumentSupport.resolvedOutputMode(outputMode));
  }

  /** Factory for one request-bound write command that also carries an output mode. */
  @FunctionalInterface
  private interface RequestBoundOutputCommandFactory {
    /** Builds one parsed write command from the resolved book, request file, and output mode. */
    CliCommand create(BookAccess bookAccess, Path requestFile, OutputMode outputMode);
  }
}
