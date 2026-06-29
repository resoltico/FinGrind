package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;

/** Parses CLI arguments for `fiscal-year-close`. */
final class CliFiscalYearCloseArguments {
  private static final List<String> FISCAL_YEAR_CLOSE_OPTIONS =
      List.of(ProtocolOptions.PERIOD_START, ProtocolOptions.PERIOD_END, ProtocolOptions.OUTPUT);
  private static final CliBookArgumentParser.CommandArgumentSpec FISCAL_YEAR_CLOSE_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.PERIOD_START, ProtocolOptions.PERIOD_END, ProtocolOptions.OUTPUT),
          List.of());

  private CliFiscalYearCloseArguments() {}

  static CliCommand parseFiscalYearCloseCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, FISCAL_YEAR_CLOSE_ARGUMENTS);
    CliReportingPeriodCommandArguments.ParsedReportingPeriodCommandArguments
        parsedFiscalYearCloseArguments =
            parseFiscalYearCloseArguments(parsedArguments.commandArguments());
    return new FiscalYearClose(
        parsedArguments.bookAccess(),
        parsedFiscalYearCloseArguments.reportingPeriod(),
        CliOptionModes.resolvedOutputMode(parsedFiscalYearCloseArguments.outputMode()));
  }

  static CliReportingPeriodCommandArguments.ParsedReportingPeriodCommandArguments
      parseFiscalYearCloseArguments(List<String> commandArguments) {
    return CliReportingPeriodCommandArguments.parse(commandArguments, FISCAL_YEAR_CLOSE_OPTIONS);
  }
}
