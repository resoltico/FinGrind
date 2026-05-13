package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/**
 * Parses request-bound mutation commands such as declare-account, execute-plan, and posting flows.
 */
final class CliRequestMutationArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec OUTPUT_ONLY_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(List.of(ProtocolOptions.OUTPUT), List.of());

  private CliRequestMutationArguments() {}

  static CliCommand parseDeclareAccountCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseRequestBoundCommandArguments(arguments, OUTPUT_ONLY_ARGUMENTS);
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      argumentIterator.next();
      outputMode =
          CliArgumentValueParser.requireOutputMode(
              outputMode,
              CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
    }
    return new DeclareAccount(
        parsedArguments.bookAccess(),
        parsedArguments.optionalRequestFile().orElseThrow(),
        CliArgumentValueParser.resolvedOutputMode(outputMode));
  }

  static CliCommand parseExecutePlanCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseRequestBoundArguments(arguments);
    return new ExecutePlan(
        parsedArguments.bookAccess(), parsedArguments.optionalRequestFile().orElseThrow());
  }

  static CliCommand parsePreflightEntryCommand(List<String> arguments) {
    return parseRequestBoundOutputCommand(arguments, PreflightEntry::new);
  }

  static CliCommand parsePostEntryCommand(List<String> arguments) {
    return parseRequestBoundOutputCommand(arguments, PostEntry::new);
  }

  private static CliCommand parseRequestBoundOutputCommand(
      List<String> arguments, RequestBoundOutputCommandFactory commandFactory) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseRequestBoundCommandArguments(arguments, OUTPUT_ONLY_ARGUMENTS);
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      argumentIterator.next();
      outputMode =
          CliArgumentValueParser.requireOutputMode(
              outputMode,
              CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
    }
    return commandFactory.create(
        parsedArguments.bookAccess(),
        parsedArguments.optionalRequestFile().orElseThrow(),
        CliArgumentValueParser.resolvedOutputMode(outputMode));
  }

  /** Factory for one request-bound write command that also carries an output mode. */
  @FunctionalInterface
  private interface RequestBoundOutputCommandFactory {
    /** Builds one parsed write command from the resolved book, request file, and output mode. */
    CliCommand create(BookAccess bookAccess, Path requestFile, OutputMode outputMode);
  }
}
