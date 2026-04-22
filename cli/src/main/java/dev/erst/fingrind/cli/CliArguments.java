package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.util.List;
import java.util.Objects;

/** Parses raw CLI arguments into the corresponding typed FinGrind command. */
final class CliArguments {
  private CliArguments() {}

  /** Parses the raw CLI arguments into the corresponding command model. */
  static CliCommand parse(String[] args) {
    Objects.requireNonNull(args, "args must not be null");
    List<String> arguments = List.of(args);
    if (arguments.isEmpty()) {
      return new CliCommand.Help(dev.erst.fingrind.contract.protocol.OutputMode.HUMAN);
    }
    ProtocolOperation operation =
        ProtocolCatalog.findByToken(arguments.getFirst())
            .orElseThrow(() -> CliArgumentValueParser.unknownCommand(arguments.getFirst()));
    return switch (operation.id()) {
      case HELP -> CliDiscoveryArguments.parseHelp(arguments);
      case VERSION -> CliDiscoveryArguments.parseVersion(arguments);
      case CAPABILITIES -> CliDiscoveryArguments.parseCapabilities(arguments);
      case PRINT_REQUEST_TEMPLATE -> CliDiscoveryArguments.parsePrintRequestTemplate(arguments);
      case PRINT_PLAN_TEMPLATE -> CliDiscoveryArguments.parsePrintPlanTemplate(arguments);
      case GENERATE_BOOK_KEY_FILE ->
          CliMutationArguments.parseGenerateBookKeyFileCommand(arguments);
      case OPEN_BOOK -> CliMutationArguments.parseOpenBookCommand(arguments);
      case REKEY_BOOK -> CliMutationArguments.parseRekeyBookCommand(arguments);
      case DECLARE_ACCOUNT -> CliMutationArguments.parseDeclareAccountCommand(arguments);
      case INSPECT_BOOK -> CliReadArguments.parseInspectBookCommand(arguments);
      case LIST_ACCOUNTS -> CliReadArguments.parseListAccountsCommand(arguments);
      case GET_POSTING -> CliReadArguments.parseGetPostingCommand(arguments);
      case LIST_POSTINGS -> CliReadArguments.parseListPostingsCommand(arguments);
      case ACCOUNT_BALANCE -> CliReadArguments.parseAccountBalanceCommand(arguments);
      case TRIAL_BALANCE -> CliReadArguments.parseTrialBalanceCommand(arguments);
      case ACCOUNT_LEDGER -> CliReadArguments.parseAccountLedgerCommand(arguments);
      case PERIOD_SUMMARY -> CliReadArguments.parsePeriodSummaryCommand(arguments);
      case EXECUTE_PLAN -> CliMutationArguments.parseExecutePlanCommand(arguments);
      case PREFLIGHT_ENTRY -> CliMutationArguments.parsePreflightEntryCommand(arguments);
      case POST_ENTRY -> CliMutationArguments.parsePostEntryCommand(arguments);
    };
  }
}
