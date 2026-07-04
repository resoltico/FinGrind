package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;

/** Parses CLI arguments for `fiscal-year-close`. */
final class CliFiscalYearCloseArguments {
  private static final List<String> FISCAL_YEAR_CLOSE_OPTIONS =
      List.of(ProtocolOptions.YEAR, ProtocolOptions.OUTPUT);
  private static final CliBookArgumentParser.CommandArgumentSpec FISCAL_YEAR_CLOSE_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.YEAR, ProtocolOptions.OUTPUT), List.of());

  private CliFiscalYearCloseArguments() {}

  static CliCommand parseFiscalYearCloseCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, FISCAL_YEAR_CLOSE_ARGUMENTS);
    ParsedFiscalYearCloseArguments parsedFiscalYearCloseArguments =
        parseFiscalYearCloseArguments(parsedArguments.commandArguments());
    return new FiscalYearClose(
        parsedArguments.bookAccess(),
        parsedFiscalYearCloseArguments.fiscalYearLabel(),
        CliOptionModes.resolvedOutputMode(parsedFiscalYearCloseArguments.outputMode()));
  }

  static ParsedFiscalYearCloseArguments parseFiscalYearCloseArguments(
      List<String> commandArguments) {
    CliCloseCommandArgumentSupport.ParsedCloseArgument<Integer> parsedArgument =
        CliCloseCommandArgumentSupport.parseSingleRequiredOption(
            commandArguments,
            ProtocolOptions.YEAR,
            FISCAL_YEAR_CLOSE_OPTIONS,
            CliOptionValues::parseYearOption);
    return new ParsedFiscalYearCloseArguments(
        parsedArgument.requiredValue(), parsedArgument.outputMode());
  }

  record ParsedFiscalYearCloseArguments(
      int fiscalYearLabel, @org.jspecify.annotations.Nullable OutputMode outputMode) {}
}
