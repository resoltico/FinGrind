package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
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
  private static final CliBookArgumentParser.CommandArgumentSpec EXECUTE_PLAN_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.OUTPUT, ProtocolOptions.RESULT_DETAIL), List.of());

  private CliRequestMutationArguments() {}

  static CliCommand parseDeclareAccountCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseRequestBoundCommandArguments(arguments, OUTPUT_ONLY_ARGUMENTS);
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      argumentIterator.next();
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }
    return new DeclareAccount(
        parsedArguments.bookAccess(),
        parsedArguments.optionalRequestFile().orElseThrow(),
        CliOptionModes.resolvedOutputMode(outputMode));
  }

  static CliCommand parseDeclareTaxRegistrationCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseRequestBoundCommandArguments(arguments, OUTPUT_ONLY_ARGUMENTS);
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      argumentIterator.next();
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }
    return new DeclareTaxRegistration(
        parsedArguments.bookAccess(),
        parsedArguments.optionalRequestFile().orElseThrow(),
        CliOptionModes.resolvedOutputMode(outputMode));
  }

  static CliCommand parseExecutePlanCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseRequestBoundCommandArguments(arguments, EXECUTE_PLAN_ARGUMENTS);
    @Nullable OutputMode outputMode = null;
    @Nullable PlanResultDetail resultDetail = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
        continue;
      }
      resultDetail = CliOptionModes.requirePlanResultDetail(resultDetail, argumentIterator);
    }
    return new ExecutePlan(
        parsedArguments.bookAccess(),
        parsedArguments.optionalRequestFile().orElseThrow(),
        CliOptionModes.resolvedOutputMode(outputMode),
        resultDetail == null ? PlanResultDetail.SUMMARY : resultDetail);
  }

  static CliCommand parsePreflightEntryCommand(List<String> arguments) {
    return parseRequestBoundOutputCommand(arguments, PreflightEntry::new);
  }

  static CliCommand parsePostEntryCommand(List<String> arguments) {
    return parseRequestBoundOutputCommand(arguments, PostEntry::new);
  }

  static CliCommand parseRecordSaleCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_SALE);
  }

  static CliCommand parseRecordExpenseCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_EXPENSE);
  }

  static CliCommand parseRecordOwnerContributionCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_OWNER_CONTRIBUTION);
  }

  static CliCommand parseRecordOwnerWithdrawalCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_OWNER_WITHDRAWAL);
  }

  static CliCommand parseRecordOpeningPositionCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_OPENING_POSITION);
  }

  static CliCommand parseRecordReversalCommand(List<String> arguments) {
    return parseRecordEntryCommand(arguments, OperationId.RECORD_REVERSAL);
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
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }
    return commandFactory.create(
        parsedArguments.bookAccess(),
        parsedArguments.optionalRequestFile().orElseThrow(),
        CliOptionModes.resolvedOutputMode(outputMode));
  }

  private static CliCommand parseRecordEntryCommand(
      List<String> arguments, OperationId operationId) {
    return parseRequestBoundOutputCommand(
        arguments,
        (bookAccess, requestFile, outputMode) ->
            new RecordEntry(bookAccess, requestFile, outputMode, operationId));
  }

  /** Factory for one request-bound write command that also carries an output mode. */
  @FunctionalInterface
  private interface RequestBoundOutputCommandFactory {
    /** Builds one parsed write command from the resolved book, request file, and output mode. */
    CliCommand create(BookAccess bookAccess, Path requestFile, OutputMode outputMode);
  }
}
