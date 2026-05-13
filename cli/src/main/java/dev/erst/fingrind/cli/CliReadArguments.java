package dev.erst.fingrind.cli;

import java.util.List;

/** Routes read-oriented CLI commands to either book-query or reporting argument parsers. */
final class CliReadArguments {
  private CliReadArguments() {}

  static CliCommand parseInspectBookCommand(List<String> arguments) {
    return CliBookQueryArguments.parseInspectBookCommand(arguments);
  }

  static CliCommand parseGetPostingCommand(List<String> arguments) {
    return CliBookQueryArguments.parseGetPostingCommand(arguments);
  }

  static CliCommand parseListAccountsCommand(List<String> arguments) {
    return CliBookQueryArguments.parseListAccountsCommand(arguments);
  }

  static CliCommand parseListPostingsCommand(List<String> arguments) {
    return CliBookQueryArguments.parseListPostingsCommand(arguments);
  }

  static CliCommand parseAccountBalanceCommand(List<String> arguments) {
    return CliReportArguments.parseAccountBalanceCommand(arguments);
  }

  static CliCommand parseTrialBalanceCommand(List<String> arguments) {
    return CliReportArguments.parseTrialBalanceCommand(arguments);
  }

  static CliCommand parseAccountLedgerCommand(List<String> arguments) {
    return CliReportArguments.parseAccountLedgerCommand(arguments);
  }

  static CliCommand parsePeriodSummaryCommand(List<String> arguments) {
    return CliReportArguments.parsePeriodSummaryCommand(arguments);
  }

  static CliCommand parseFinancialPositionCommand(List<String> arguments) {
    return CliReportArguments.parseFinancialPositionCommand(arguments);
  }

  static CliCommand parseIncomeStatementCommand(List<String> arguments) {
    return CliReportArguments.parseIncomeStatementCommand(arguments);
  }

  static CliCommand parseChangesInEquityCommand(List<String> arguments) {
    return CliReportArguments.parseChangesInEquityCommand(arguments);
  }
}
