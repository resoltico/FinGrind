package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Shared CLI parsing for commands that accept one bounded reporting period and output mode. */
final class CliReportingPeriodCommandArguments {
  private CliReportingPeriodCommandArguments() {}

  static ParsedReportingPeriodCommandArguments parse(
      List<String> commandArguments, List<String> supportedOptions) {
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = commandArguments.listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.PERIOD_START.equals(argument)) {
        effectiveDateFrom =
            CliReportArguments.requireDateOption(
                effectiveDateFrom, argumentIterator, ProtocolOptions.PERIOD_START);
        continue;
      }
      if (ProtocolOptions.PERIOD_END.equals(argument)) {
        effectiveDateTo =
            CliReportArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.PERIOD_END);
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
    if (effectiveDateFrom == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.PERIOD_START,
          "A " + ProtocolOptions.PERIOD_START + " argument is required.");
    }
    if (effectiveDateTo == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.PERIOD_END, "A " + ProtocolOptions.PERIOD_END + " argument is required.");
    }
    LocalDate resolvedEffectiveDateFrom = effectiveDateFrom;
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    CliArgumentValueParser.requireOrderedDateRange(
        resolvedEffectiveDateFrom,
        resolvedEffectiveDateTo,
        ProtocolOptions.PERIOD_START,
        ProtocolOptions.PERIOD_END);
    return new ParsedReportingPeriodCommandArguments(
        new ReportingPeriod(resolvedEffectiveDateFrom, resolvedEffectiveDateTo), outputMode);
  }

  record ParsedReportingPeriodCommandArguments(
      ReportingPeriod reportingPeriod, @Nullable OutputMode outputMode) {}
}
