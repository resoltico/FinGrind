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

  static CliCommand parseFinancialPositionCommand(List<String> arguments) {
    return CliSummaryReportArguments.parseFinancialPositionCommand(arguments);
  }

  static CliCommand parseIncomeStatementCommand(List<String> arguments) {
    return CliSummaryReportArguments.parseIncomeStatementCommand(arguments);
  }

  static CliCommand parseChangesInEquityCommand(List<String> arguments) {
    return CliSummaryReportArguments.parseChangesInEquityCommand(arguments);
  }

  static @Nullable LocalDate requireDateOption(
      @Nullable LocalDate currentValue, ListIterator<String> argumentIterator, String optionName) {
    if (currentValue != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.parseLocalDateOption(
        CliOptionValues.requireValue(argumentIterator, optionName), optionName);
  }

  static @Nullable OutputMode requireReportOutputMode(
      @Nullable OutputMode currentOutputMode, ListIterator<String> argumentIterator) {
    String rawOutputMode = CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT);
    if ("pdf".equals(rawOutputMode)) {
      throw CliArgumentValueParser.unsupportedOutputSelection(
          ProtocolOptions.OUTPUT,
          "Unsupported output mode for "
              + ProtocolOptions.OUTPUT
              + ": "
              + rawOutputMode
              + ". PDF export is file-only; use "
              + ProtocolOptions.PDF_OUT
              + " <path> together with one stdout mode from json, text, csv.");
    }
    return CliOptionModes.requireOutputMode(
        currentOutputMode,
        rawOutputMode,
        CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV));
  }
}
