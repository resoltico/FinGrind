package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Registry that maps each public operation id onto the corresponding typed CLI parser. */
final class CliCommandParsingRegistry {
  private static final Map<OperationId, Function<List<String>, CliCommand>> PARSERS =
      Map.ofEntries(
          Map.entry(OperationId.HELP, CliDiscoveryArguments::parseHelp),
          Map.entry(OperationId.VERSION, CliDiscoveryArguments::parseVersion),
          Map.entry(OperationId.CAPABILITIES, CliDiscoveryArguments::parseCapabilities),
          Map.entry(OperationId.ENVIRONMENT, CliDiscoveryArguments::parseEnvironment),
          Map.entry(
              OperationId.PRINT_REQUEST_TEMPLATE, CliDiscoveryArguments::parsePrintRequestTemplate),
          Map.entry(OperationId.PRINT_PLAN_TEMPLATE, CliDiscoveryArguments::parsePrintPlanTemplate),
          Map.entry(
              OperationId.GENERATE_BOOK_KEY_FILE,
              CliLifecycleMutationArguments::parseGenerateBookKeyFileCommand),
          Map.entry(OperationId.OPEN_BOOK, CliLifecycleMutationArguments::parseOpenBookCommand),
          Map.entry(OperationId.REKEY_BOOK, CliLifecycleMutationArguments::parseRekeyBookCommand),
          Map.entry(OperationId.BACKUP_BOOK, CliLifecycleMutationArguments::parseBackupBookCommand),
          Map.entry(
              OperationId.RESTORE_BOOK, CliLifecycleMutationArguments::parseRestoreBookCommand),
          Map.entry(
              OperationId.INSPECT_REKEY_ROLLBACK,
              CliLifecycleMutationArguments::parseInspectRekeyRollbackCommand),
          Map.entry(
              OperationId.DELETE_REKEY_ROLLBACK,
              CliLifecycleMutationArguments::parseDeleteRekeyRollbackCommand),
          Map.entry(
              OperationId.RESTORE_REKEY_ROLLBACK,
              CliLifecycleMutationArguments::parseRestoreRekeyRollbackCommand),
          Map.entry(
              OperationId.DECLARE_ACCOUNT, CliRequestMutationArguments::parseDeclareAccountCommand),
          Map.entry(
              OperationId.TRANSFER_PERIOD_RESULT,
              CliLifecycleMutationArguments::parsePeriodResultTransferCommand),
          Map.entry(OperationId.INSPECT_BOOK, CliBookQueryArguments::parseInspectBookCommand),
          Map.entry(OperationId.LIST_ACCOUNTS, CliBookQueryArguments::parseListAccountsCommand),
          Map.entry(OperationId.GET_POSTING, CliBookQueryArguments::parseGetPostingCommand),
          Map.entry(OperationId.LIST_POSTINGS, CliBookQueryArguments::parseListPostingsCommand),
          Map.entry(OperationId.ACCOUNT_BALANCE, CliReportArguments::parseAccountBalanceCommand),
          Map.entry(OperationId.TRIAL_BALANCE, CliReportArguments::parseTrialBalanceCommand),
          Map.entry(OperationId.ACCOUNT_LEDGER, CliReportArguments::parseAccountLedgerCommand),
          Map.entry(OperationId.PERIOD_SUMMARY, CliReportArguments::parsePeriodSummaryCommand),
          Map.entry(
              OperationId.FINANCIAL_POSITION, CliReportArguments::parseFinancialPositionCommand),
          Map.entry(OperationId.INCOME_STATEMENT, CliReportArguments::parseIncomeStatementCommand),
          Map.entry(OperationId.CHANGES_IN_EQUITY, CliReportArguments::parseChangesInEquityCommand),
          Map.entry(OperationId.EXECUTE_PLAN, CliRequestMutationArguments::parseExecutePlanCommand),
          Map.entry(
              OperationId.PREFLIGHT_ENTRY, CliRequestMutationArguments::parsePreflightEntryCommand),
          Map.entry(OperationId.POST_ENTRY, CliRequestMutationArguments::parsePostEntryCommand));

  private CliCommandParsingRegistry() {}

  static CliCommand parse(OperationId operationId, List<String> arguments) {
    Objects.requireNonNull(operationId, "operationId");
    Objects.requireNonNull(arguments, "arguments");
    return requiredParser(operationId, PARSERS).apply(arguments);
  }

  static Function<List<String>, CliCommand> requiredParser(
      OperationId operationId, Map<OperationId, Function<List<String>, CliCommand>> parsers) {
    Function<List<String>, CliCommand> parser = parsers.get(operationId);
    if (parser == null) {
      throw new IllegalArgumentException("No CLI parser registered for " + operationId.wireName());
    }
    return parser;
  }
}
