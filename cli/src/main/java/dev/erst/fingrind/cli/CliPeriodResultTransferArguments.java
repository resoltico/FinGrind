package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses CLI arguments for `transfer-period-result`. */
final class CliPeriodResultTransferArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec TRANSFER_PERIOD_RESULT_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.EFFECTIVE_DATE_FROM,
              ProtocolOptions.EFFECTIVE_DATE_TO,
              ProtocolOptions.OUTPUT),
          List.of());

  private CliPeriodResultTransferArguments() {}

  static CliCommand parsePeriodResultTransferCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(
            arguments, TRANSFER_PERIOD_RESULT_ARGUMENTS);
    ParsedTransferPeriodResultArguments parsedTransferPeriodResultArguments =
        parseTransferPeriodResultArguments(parsedArguments.commandArguments());
    return new TransferPeriodResult(
        parsedArguments.bookAccess(),
        parsedTransferPeriodResultArguments.reportingPeriod(),
        CliOptionModes.resolvedOutputMode(parsedTransferPeriodResultArguments.outputMode()));
  }

  static ParsedTransferPeriodResultArguments parseTransferPeriodResultArguments(
      List<String> commandArguments) {
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = commandArguments.listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.EFFECTIVE_DATE_FROM.equals(argument)) {
        effectiveDateFrom =
            CliReportArguments.requireDateOption(
                effectiveDateFrom, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_FROM);
        continue;
      }
      if (ProtocolOptions.EFFECTIVE_DATE_TO.equals(argument)) {
        effectiveDateTo =
            CliReportArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_TO);
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
      throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
    }
    if (effectiveDateFrom == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.EFFECTIVE_DATE_FROM,
          "A " + ProtocolOptions.EFFECTIVE_DATE_FROM + " argument is required.");
    }
    if (effectiveDateTo == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.EFFECTIVE_DATE_TO,
          "A " + ProtocolOptions.EFFECTIVE_DATE_TO + " argument is required.");
    }
    LocalDate resolvedEffectiveDateFrom = effectiveDateFrom;
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    CliArgumentValueParser.requireOrderedDateRange(
        resolvedEffectiveDateFrom,
        resolvedEffectiveDateTo,
        ProtocolOptions.EFFECTIVE_DATE_FROM,
        ProtocolOptions.EFFECTIVE_DATE_TO);
    return new ParsedTransferPeriodResultArguments(
        new ReportingPeriod(resolvedEffectiveDateFrom, resolvedEffectiveDateTo), outputMode);
  }

  record ParsedTransferPeriodResultArguments(
      ReportingPeriod reportingPeriod, @Nullable OutputMode outputMode) {}
}
