package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.time.LocalDate;
import java.util.List;

/** Parses CLI arguments for `interim-result-sweep`. */
final class CliInterimResultSweepArguments {
  private static final List<String> INTERIM_RESULT_SWEEP_OPTIONS =
      List.of(ProtocolOptions.DateRange.THROUGH, ProtocolOptions.Presentation.OUTPUT);
  private static final CliBookArgumentParser.CommandArgumentSpec INTERIM_RESULT_SWEEP_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.DateRange.THROUGH, ProtocolOptions.Presentation.OUTPUT),
          List.of());

  private CliInterimResultSweepArguments() {}

  static CliCommand parseInterimResultSweepCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(
            arguments, INTERIM_RESULT_SWEEP_ARGUMENTS);
    ParsedInterimResultSweepArguments parsedInterimResultSweepArguments =
        parseInterimResultSweepArguments(parsedArguments.commandArguments());
    return new InterimResultSweep(
        parsedArguments.bookAccess(),
        parsedInterimResultSweepArguments.throughEffectiveDate(),
        CliOptionModes.resolvedOutputMode(parsedInterimResultSweepArguments.outputMode()));
  }

  static ParsedInterimResultSweepArguments parseInterimResultSweepArguments(
      List<String> commandArguments) {
    CliCloseCommandArgumentSupport.ParsedCloseArgument<LocalDate> parsedArgument =
        CliCloseCommandArgumentSupport.parseSingleRequiredOption(
            commandArguments,
            ProtocolOptions.DateRange.THROUGH,
            INTERIM_RESULT_SWEEP_OPTIONS,
            CliOptionValues::parseLocalDateOption);
    return new ParsedInterimResultSweepArguments(
        parsedArgument.requiredValue(), parsedArgument.outputMode());
  }

  record ParsedInterimResultSweepArguments(
      LocalDate throughEffectiveDate, @org.jspecify.annotations.Nullable OutputMode outputMode) {}
}
