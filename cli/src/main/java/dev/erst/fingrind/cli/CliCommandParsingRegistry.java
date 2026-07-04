package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Registry that maps each public operation id onto the corresponding typed CLI parser. */
final class CliCommandParsingRegistry {
  private static final ParserCatalog PARSERS = new ParserCatalog(validatedParsers(parserMap()));

  private CliCommandParsingRegistry() {}

  private static Map<OperationId, Function<List<String>, CliCommand>> parserMap() {
    return ParserCatalog.bindingsAsMap(
        discoveryBindings(),
        lifecycleBindings(),
        queryBindings(),
        reportBindings(),
        writeBindings());
  }

  @SafeVarargs
  static Map<OperationId, Function<List<String>, CliCommand>> parserMapForEntries(
      Map.Entry<OperationId, Function<List<String>, CliCommand>>... entries) {
    ParserBinding[] bindings = new ParserBinding[entries.length];
    for (int index = 0; index < entries.length; index++) {
      Map.Entry<OperationId, Function<List<String>, CliCommand>> entry = entries[index];
      bindings[index] = binding(entry.getKey(), entry.getValue());
    }
    return ParserCatalog.bindingsAsMap(bindings);
  }

  private static ParserBinding[] discoveryBindings() {
    return new ParserBinding[] {
      binding(OperationId.HELP, CliDiscoveryArguments::parseHelp),
      binding(OperationId.VERSION, CliDiscoveryArguments::parseVersion),
      binding(OperationId.CAPABILITIES, CliDiscoveryArguments::parseCapabilities),
      binding(OperationId.ENVIRONMENT, CliDiscoveryArguments::parseEnvironment),
      binding(OperationId.PRINT_REQUEST_TEMPLATE, CliDiscoveryArguments::parsePrintRequestTemplate),
      binding(OperationId.PRINT_PLAN_TEMPLATE, CliDiscoveryArguments::parsePrintPlanTemplate)
    };
  }

  private static ParserBinding[] lifecycleBindings() {
    return new ParserBinding[] {
      binding(
          OperationId.GENERATE_BOOK_KEY_FILE,
          CliLifecycleMutationArguments::parseGenerateBookKeyFileCommand),
      binding(OperationId.OPEN_BOOK, CliLifecycleMutationArguments::parseOpenBookCommand),
      binding(OperationId.REKEY_BOOK, CliLifecycleMutationArguments::parseRekeyBookCommand),
      binding(OperationId.BACKUP_BOOK, CliLifecycleMutationArguments::parseBackupBookCommand),
      binding(OperationId.RESTORE_BOOK, CliLifecycleMutationArguments::parseRestoreBookCommand),
      binding(
          OperationId.INSPECT_REKEY_ROLLBACK,
          CliLifecycleMutationArguments::parseInspectRekeyRollbackCommand),
      binding(
          OperationId.DELETE_REKEY_ROLLBACK,
          CliLifecycleMutationArguments::parseDeleteRekeyRollbackCommand),
      binding(
          OperationId.RESTORE_REKEY_ROLLBACK,
          CliLifecycleMutationArguments::parseRestoreRekeyRollbackCommand),
      binding(
          OperationId.INTERIM_RESULT_SWEEP,
          CliLifecycleMutationArguments::parseInterimResultSweepCommand),
      binding(
          OperationId.FISCAL_YEAR_CLOSE, CliLifecycleMutationArguments::parseFiscalYearCloseCommand)
    };
  }

  private static ParserBinding[] queryBindings() {
    return new ParserBinding[] {
      binding(OperationId.INSPECT_BOOK, CliBookQueryArguments::parseInspectBookCommand),
      binding(OperationId.LIST_ACCOUNTS, CliBookQueryArguments::parseListAccountsCommand),
      binding(
          OperationId.LIST_TAX_REGISTRATIONS,
          CliTaxQueryArguments::parseListTaxRegistrationsCommand),
      binding(OperationId.GET_POSTING, CliBookQueryArguments::parseGetPostingCommand),
      binding(OperationId.LIST_POSTINGS, CliBookQueryArguments::parseListPostingsCommand)
    };
  }

  private static ParserBinding[] reportBindings() {
    return new ParserBinding[] {
      binding(OperationId.TAX_OBLIGATION, CliTaxQueryArguments::parseTaxObligationCommand),
      binding(OperationId.ACCOUNT_BALANCE, CliReportArguments::parseAccountBalanceCommand),
      binding(OperationId.TRIAL_BALANCE, CliReportArguments::parseTrialBalanceCommand),
      binding(OperationId.ACCOUNT_LEDGER, CliReportArguments::parseAccountLedgerCommand),
      binding(OperationId.PERIOD_SUMMARY, CliReportArguments::parsePeriodSummaryCommand),
      binding(OperationId.FINANCIAL_POSITION, CliReportArguments::parseFinancialPositionCommand),
      binding(OperationId.INCOME_STATEMENT, CliReportArguments::parseIncomeStatementCommand),
      binding(OperationId.CASH_FLOW_STATEMENT, CliReportArguments::parseCashFlowStatementCommand),
      binding(OperationId.CHANGES_IN_EQUITY, CliReportArguments::parseChangesInEquityCommand)
    };
  }

  private static ParserBinding[] writeBindings() {
    return new ParserBinding[] {
      binding(OperationId.DECLARE_ACCOUNT, CliRequestMutationArguments::parseDeclareAccountCommand),
      binding(
          OperationId.DECLARE_TAX_REGISTRATION,
          CliRequestMutationArguments::parseDeclareTaxRegistrationCommand),
      binding(OperationId.EXECUTE_PLAN, CliRequestMutationArguments::parseExecutePlanCommand),
      binding(OperationId.PREFLIGHT_ENTRY, CliPostingMutationArguments::parsePreflightEntryCommand),
      binding(
          OperationId.RECORD_SALE_SETTLED,
          CliPostingMutationArguments::parseRecordSaleSettledCommand),
      binding(
          OperationId.RECORD_SALE_ON_CREDIT,
          CliPostingMutationArguments::parseRecordSaleOnCreditCommand),
      binding(
          OperationId.RECORD_PURCHASE_SETTLED,
          CliPostingMutationArguments::parseRecordPurchaseSettledCommand),
      binding(
          OperationId.RECORD_PURCHASE_ON_CREDIT,
          CliPostingMutationArguments::parseRecordPurchaseOnCreditCommand),
      binding(
          OperationId.RECORD_EXPENSE_SETTLED,
          CliPostingMutationArguments::parseRecordExpenseSettledCommand),
      binding(
          OperationId.RECORD_EXPENSE_ON_CREDIT,
          CliPostingMutationArguments::parseRecordExpenseOnCreditCommand),
      binding(OperationId.RECORD_RECEIPT, CliPostingMutationArguments::parseRecordReceiptCommand),
      binding(OperationId.RECORD_PAYMENT, CliPostingMutationArguments::parseRecordPaymentCommand),
      binding(
          OperationId.RECORD_OWNER_CONTRIBUTION,
          CliPostingMutationArguments::parseRecordOwnerContributionCommand),
      binding(
          OperationId.RECORD_OWNER_WITHDRAWAL,
          CliPostingMutationArguments::parseRecordOwnerWithdrawalCommand),
      binding(
          OperationId.RECORD_OPENING_POSITION,
          CliPostingMutationArguments::parseRecordOpeningPositionCommand),
      binding(OperationId.RECORD_REVERSAL, CliPostingMutationArguments::parseRecordReversalCommand),
      binding(OperationId.POST_ENTRY, CliPostingMutationArguments::parsePostEntryCommand)
    };
  }

  private static ParserBinding binding(
      OperationId operationId, Function<List<String>, CliCommand> parser) {
    return new ParserBinding(operationId, parser);
  }

  static CliCommand parse(OperationId operationId, List<String> arguments) {
    Objects.requireNonNull(operationId, "operationId");
    Objects.requireNonNull(arguments, "arguments");
    return PARSERS.requiredParser(operationId).apply(arguments);
  }

  static Function<List<String>, CliCommand> requiredParser(
      OperationId operationId, Map<OperationId, Function<List<String>, CliCommand>> parsers) {
    Function<List<String>, CliCommand> parser = parsers.get(operationId);
    if (parser == null) {
      throw new IllegalArgumentException("No CLI parser registered for " + operationId.wireName());
    }
    return parser;
  }

  static Set<OperationId> registeredOperationIds() {
    return PARSERS.registeredOperationIds();
  }

  static Map<OperationId, Function<List<String>, CliCommand>> validatedParsers(
      Map<OperationId, Function<List<String>, CliCommand>> parsers) {
    Set<OperationId> registeredOperationIds = Set.copyOf(parsers.keySet());
    Set<OperationId> catalogOperationIds = new LinkedHashSet<>();
    for (ProtocolOperation operation : ProtocolCatalog.operations()) {
      catalogOperationIds.add(operation.id());
    }
    if (!registeredOperationIds.equals(catalogOperationIds)) {
      Set<OperationId> missingOperationIds = new LinkedHashSet<>(catalogOperationIds);
      missingOperationIds.removeAll(registeredOperationIds);
      Set<OperationId> unexpectedOperationIds = new LinkedHashSet<>(registeredOperationIds);
      unexpectedOperationIds.removeAll(catalogOperationIds);
      throw new IllegalStateException(
          "CLI parser registry must match ProtocolCatalog operations. Missing: "
              + missingOperationIds
              + "; unexpected: "
              + unexpectedOperationIds);
    }
    return parsers;
  }

  private record ParserCatalog(Map<OperationId, Function<List<String>, CliCommand>> parsers) {
    private ParserCatalog {
      parsers = Map.copyOf(parsers);
    }

    private static Map<OperationId, Function<List<String>, CliCommand>> bindingsAsMap(
        ParserBinding[]... bindingGroups) {
      Map<OperationId, Function<List<String>, CliCommand>> parsers = new ConcurrentHashMap<>();
      for (ParserBinding[] bindingGroup : bindingGroups) {
        for (ParserBinding binding : bindingGroup) {
          Function<List<String>, CliCommand> previous =
              parsers.put(binding.operationId(), binding.parser());
          if (previous != null) {
            throw new IllegalStateException(
                "Duplicate CLI parser registered for " + binding.operationId().wireName());
          }
        }
      }
      return Map.copyOf(parsers);
    }

    private Function<List<String>, CliCommand> requiredParser(OperationId operationId) {
      return CliCommandParsingRegistry.requiredParser(operationId, parsers);
    }

    private Set<OperationId> registeredOperationIds() {
      return parsers.keySet();
    }
  }

  private record ParserBinding(OperationId operationId, Function<List<String>, CliCommand> parser) {
    private ParserBinding {
      Objects.requireNonNull(operationId, "operationId");
      Objects.requireNonNull(parser, "parser");
    }
  }
}
