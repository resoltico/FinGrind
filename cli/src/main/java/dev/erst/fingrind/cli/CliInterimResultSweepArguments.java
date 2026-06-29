package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;

/** Parses CLI arguments for `interim-result-sweep`. */
final class CliInterimResultSweepArguments {
  private static final List<String> INTERIM_RESULT_SWEEP_OPTIONS =
      List.of(ProtocolOptions.PERIOD_START, ProtocolOptions.PERIOD_END, ProtocolOptions.OUTPUT);
  private static final CliBookArgumentParser.CommandArgumentSpec INTERIM_RESULT_SWEEP_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.PERIOD_START, ProtocolOptions.PERIOD_END, ProtocolOptions.OUTPUT),
          List.of());

  private CliInterimResultSweepArguments() {}

  static CliCommand parseInterimResultSweepCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(
            arguments, INTERIM_RESULT_SWEEP_ARGUMENTS);
    CliReportingPeriodCommandArguments.ParsedReportingPeriodCommandArguments
        parsedInterimResultSweepArguments =
            parseInterimResultSweepArguments(parsedArguments.commandArguments());
    return new InterimResultSweep(
        parsedArguments.bookAccess(),
        parsedInterimResultSweepArguments.reportingPeriod(),
        CliOptionModes.resolvedOutputMode(parsedInterimResultSweepArguments.outputMode()));
  }

  static CliReportingPeriodCommandArguments.ParsedReportingPeriodCommandArguments
      parseInterimResultSweepArguments(List<String> commandArguments) {
    return CliReportingPeriodCommandArguments.parse(commandArguments, INTERIM_RESULT_SWEEP_OPTIONS);
  }
}
