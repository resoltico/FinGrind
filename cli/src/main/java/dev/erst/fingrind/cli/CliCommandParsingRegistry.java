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
          Map.entry(
              OperationId.PRINT_REQUEST_TEMPLATE, CliDiscoveryArguments::parsePrintRequestTemplate),
          Map.entry(OperationId.PRINT_PLAN_TEMPLATE, CliDiscoveryArguments::parsePrintPlanTemplate),
          Map.entry(
              OperationId.GENERATE_BOOK_KEY_FILE,
              CliMutationArguments::parseGenerateBookKeyFileCommand),
          Map.entry(OperationId.OPEN_BOOK, CliMutationArguments::parseOpenBookCommand),
          Map.entry(OperationId.REKEY_BOOK, CliMutationArguments::parseRekeyBookCommand),
          Map.entry(OperationId.DECLARE_ACCOUNT, CliMutationArguments::parseDeclareAccountCommand),
          Map.entry(OperationId.INSPECT_BOOK, CliReadArguments::parseInspectBookCommand),
          Map.entry(OperationId.LIST_ACCOUNTS, CliReadArguments::parseListAccountsCommand),
          Map.entry(OperationId.GET_POSTING, CliReadArguments::parseGetPostingCommand),
          Map.entry(OperationId.LIST_POSTINGS, CliReadArguments::parseListPostingsCommand),
          Map.entry(OperationId.ACCOUNT_BALANCE, CliReadArguments::parseAccountBalanceCommand),
          Map.entry(OperationId.TRIAL_BALANCE, CliReadArguments::parseTrialBalanceCommand),
          Map.entry(OperationId.ACCOUNT_LEDGER, CliReadArguments::parseAccountLedgerCommand),
          Map.entry(OperationId.PERIOD_SUMMARY, CliReadArguments::parsePeriodSummaryCommand),
          Map.entry(OperationId.EXECUTE_PLAN, CliMutationArguments::parseExecutePlanCommand),
          Map.entry(OperationId.PREFLIGHT_ENTRY, CliMutationArguments::parsePreflightEntryCommand),
          Map.entry(OperationId.POST_ENTRY, CliMutationArguments::parsePostEntryCommand));

  private CliCommandParsingRegistry() {}

  static CliCommand parse(OperationId operationId, List<String> arguments) {
    Objects.requireNonNull(operationId, "operationId");
    Objects.requireNonNull(arguments, "arguments");
    return requiredParser(operationId).apply(arguments);
  }

  private static Function<List<String>, CliCommand> requiredParser(OperationId operationId) {
    return Objects.requireNonNull(PARSERS.get(operationId));
  }
}
