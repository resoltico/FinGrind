package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses non-posting request-bound mutation commands such as declare-account and execute-plan. */
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
}
