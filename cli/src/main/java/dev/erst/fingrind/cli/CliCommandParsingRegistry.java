package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestTopics;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

/** Registry that maps each public operation id onto the corresponding typed CLI parser. */
final class CliCommandParsingRegistry {
  private static final Map<OperationId, Function<List<String>, CliCommand>> EXPLICIT_WRITE_PARSERS =
      Map.of(
          OperationId.DECLARE_ACCOUNT, CliRequestMutationArguments::parseDeclareAccountCommand,
          OperationId.AMEND_ACCOUNT, CliRequestMutationArguments::parseAmendAccountCommand,
          OperationId.RETIRE_ACCOUNT, CliRequestMutationArguments::parseRetireAccountCommand,
          OperationId.DECLARE_TAX_REGISTRATION,
              CliRequestMutationArguments::parseDeclareTaxRegistrationCommand,
          OperationId.EXECUTE_PLAN, CliRequestMutationArguments::parseExecutePlanCommand,
          OperationId.PREFLIGHT_ENTRY, CliPostingMutationArguments::parsePreflightEntryCommand,
          OperationId.POST_ENTRY, CliPostingMutationArguments::parsePostEntryCommand);
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
      binding(OperationId.ACCOUNT_BALANCE, CliAccountReportArguments::parseAccountBalanceCommand),
      binding(OperationId.TRIAL_BALANCE, CliSummaryReportArguments::parseTrialBalanceCommand),
      binding(OperationId.ACCOUNT_LEDGER, CliAccountReportArguments::parseAccountLedgerCommand),
      binding(OperationId.PERIOD_SUMMARY, CliSummaryReportArguments::parsePeriodSummaryCommand),
      binding(
          OperationId.FINANCIAL_POSITION, CliSummaryReportArguments::parseFinancialPositionCommand),
      binding(
          OperationId.INVENTORY_VALUATION,
          CliInventoryValuationArguments::parseInventoryValuationCommand),
      binding(
          OperationId.ACCRUAL_CUTOFF_SCHEDULE,
          CliAccrualCutoffScheduleArguments::parseAccrualCutoffScheduleCommand),
      binding(
          OperationId.FIXED_ASSET_REGISTER,
          CliFixedAssetRegisterArguments::parseFixedAssetRegisterCommand),
      binding(
          OperationId.FINANCING_REGISTER,
          CliFinancingRegisterArguments::parseFinancingRegisterCommand),
      binding(
          OperationId.REALIZED_FOREIGN_EXCHANGE_REGISTER,
          CliRealizedForeignExchangeRegisterArguments::parseRealizedForeignExchangeRegisterCommand),
      binding(
          OperationId.LATVIAN_PAYROLL_REGISTER,
          CliLatvianPayrollRegisterArguments::parseLatvianPayrollRegisterCommand),
      binding(OperationId.INCOME_STATEMENT, CliSummaryReportArguments::parseIncomeStatementCommand),
      binding(
          OperationId.CASH_FLOW_STATEMENT,
          CliSummaryReportArguments::parseCashFlowStatementCommand),
      binding(OperationId.CHANGES_IN_EQUITY, CliSummaryReportArguments::parseChangesInEquityCommand)
    };
  }

  private static ParserBinding[] writeBindings() {
    return Stream.concat(
            accountRegistryMutationOperationIds(),
            ProtocolCatalog.operations().stream()
                .filter(operation -> operation.category() == OperationCategory.WRITE)
                .map(ProtocolOperation::id))
        .map(operationId -> binding(operationId, writeParser(operationId)))
        .toArray(ParserBinding[]::new);
  }

  private static Stream<OperationId> accountRegistryMutationOperationIds() {
    return Stream.of(
        OperationId.DECLARE_ACCOUNT,
        OperationId.AMEND_ACCOUNT,
        OperationId.RETIRE_ACCOUNT,
        OperationId.DECLARE_TAX_REGISTRATION);
  }

  static Function<List<String>, CliCommand> writeParser(OperationId operationId) {
    Function<List<String>, CliCommand> explicitParser = EXPLICIT_WRITE_PARSERS.get(operationId);
    if (explicitParser != null) {
      return explicitParser;
    }
    if (ProtocolPostingRequestTopics.requiredEntryKind(operationId).isPresent()) {
      return arguments ->
          CliPostingMutationArguments.parseRecordEntryCommand(arguments, operationId);
    }
    throw new IllegalArgumentException(
        "No write-command parser is owned for " + operationId.wireName() + ".");
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
