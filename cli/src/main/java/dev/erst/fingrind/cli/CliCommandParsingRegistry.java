package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Registry that maps each public operation id onto the corresponding typed CLI parser. */
final class CliCommandParsingRegistry {
  private CliCommandParsingRegistry() {}

  static CliCommand parse(OperationId operationId, List<String> arguments) {
    Objects.requireNonNull(operationId, "operationId");
    Objects.requireNonNull(arguments, "arguments");
    return requiredParser(operationId).apply(arguments);
  }

  private static Function<List<String>, CliCommand> requiredParser(OperationId operationId) {
    return switch (operationId) {
      case HELP -> CliDiscoveryArguments::parseHelp;
      case VERSION -> CliDiscoveryArguments::parseVersion;
      case CAPABILITIES -> CliDiscoveryArguments::parseCapabilities;
      case PRINT_REQUEST_TEMPLATE -> CliDiscoveryArguments::parsePrintRequestTemplate;
      case PRINT_PLAN_TEMPLATE -> CliDiscoveryArguments::parsePrintPlanTemplate;
      case GENERATE_BOOK_KEY_FILE -> CliMutationArguments::parseGenerateBookKeyFileCommand;
      case OPEN_BOOK -> CliMutationArguments::parseOpenBookCommand;
      case REKEY_BOOK -> CliMutationArguments::parseRekeyBookCommand;
      case DECLARE_ACCOUNT -> CliMutationArguments::parseDeclareAccountCommand;
      case CLOSE_PERIOD -> CliMutationArguments::parseClosePeriodCommand;
      case INSPECT_BOOK -> CliReadArguments::parseInspectBookCommand;
      case LIST_ACCOUNTS -> CliReadArguments::parseListAccountsCommand;
      case GET_POSTING -> CliReadArguments::parseGetPostingCommand;
      case LIST_POSTINGS -> CliReadArguments::parseListPostingsCommand;
      case ACCOUNT_BALANCE -> CliReadArguments::parseAccountBalanceCommand;
      case TRIAL_BALANCE -> CliReadArguments::parseTrialBalanceCommand;
      case ACCOUNT_LEDGER -> CliReadArguments::parseAccountLedgerCommand;
      case PERIOD_SUMMARY -> CliReadArguments::parsePeriodSummaryCommand;
      case FINANCIAL_POSITION -> CliReadArguments::parseFinancialPositionCommand;
      case INCOME_STATEMENT -> CliReadArguments::parseIncomeStatementCommand;
      case CHANGES_IN_EQUITY -> CliReadArguments::parseChangesInEquityCommand;
      case EXECUTE_PLAN -> CliMutationArguments::parseExecutePlanCommand;
      case PREFLIGHT_ENTRY -> CliMutationArguments::parsePreflightEntryCommand;
      case POST_ENTRY -> CliMutationArguments::parsePostEntryCommand;
    };
  }
}
