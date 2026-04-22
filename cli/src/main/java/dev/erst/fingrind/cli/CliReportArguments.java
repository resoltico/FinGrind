package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Routes report command parsing to narrower account-scoped and summary-specific parsers. */
final class CliReportArguments {
  private CliReportArguments() {}

  static CliCommand parseAccountBalanceCommand(List<String> arguments) {
    return CliAccountReportArguments.parseAccountBalanceCommand(arguments);
  }

  static CliCommand parseTrialBalanceCommand(List<String> arguments) {
    return CliSummaryReportArguments.parseTrialBalanceCommand(arguments);
  }

  static CliCommand parseAccountLedgerCommand(List<String> arguments) {
    return CliAccountReportArguments.parseAccountLedgerCommand(arguments);
  }

  static CliCommand parsePeriodSummaryCommand(List<String> arguments) {
    return CliSummaryReportArguments.parsePeriodSummaryCommand(arguments);
  }

  static @Nullable LocalDate requireDateOption(
      @Nullable LocalDate currentValue, ListIterator<String> argumentIterator, String optionName) {
    if (currentValue != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliArgumentValueParser.parseLocalDateOption(
        CliArgumentValueParser.requireValue(argumentIterator, optionName), optionName);
  }

  static @Nullable OutputMode requireReportOutputMode(
      @Nullable OutputMode currentOutputMode, ListIterator<String> argumentIterator) {
    return CliArgumentValueParser.requireOutputMode(
        currentOutputMode,
        CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
        CliArgumentValueParser.supportedOutputModes(
            OutputMode.JSON, OutputMode.HUMAN, OutputMode.CSV));
  }
}
