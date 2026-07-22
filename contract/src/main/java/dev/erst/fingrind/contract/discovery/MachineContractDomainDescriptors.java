package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.BookModelFacts;
import dev.erst.fingrind.contract.protocol.CurrencyFacts;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.PlanExecutionFacts;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Builds the non-request/non-response descriptor families in the machine contract. */
final class MachineContractDomainDescriptors {
  private static final Set<Integer> TEMPLATE_EXIT_CODES = Set.of(0, 1, 70);
  private static final Set<Integer> DISCOVERY_EXIT_CODES = Set.of(0, 1, 2, 70);
  private static final Set<Integer> KEY_GENERATION_EXIT_CODES = Set.of(0, 1, 2, 6, 7, 70);
  private static final Set<Integer> MAINTENANCE_EXIT_CODES = Set.of(0, 1, 2, 4, 5, 6, 7, 70);
  private static final Set<Integer> EXECUTE_PLAN_EXIT_CODES = Set.of(0, 1, 2, 3, 4, 5, 6, 70);
  private static final Set<Integer> DEFAULT_MUTATION_EXIT_CODES = Set.of(0, 1, 2, 4, 5, 6, 70);
  private static final Set<OperationId> TEMPLATE_ONLY_OPERATIONS =
      EnumSet.of(OperationId.PRINT_REQUEST_TEMPLATE, OperationId.PRINT_PLAN_TEMPLATE);
  private static final Set<OperationId> DISCOVERY_OPERATIONS =
      EnumSet.of(
          OperationId.HELP, OperationId.VERSION, OperationId.CAPABILITIES, OperationId.ENVIRONMENT);
  private static final Set<OperationId> MAINTENANCE_OPERATIONS =
      EnumSet.of(
          OperationId.REKEY_BOOK,
          OperationId.BACKUP_BOOK,
          OperationId.RESTORE_BOOK,
          OperationId.VERIFY_BOOK,
          OperationId.ATTESTATION_REVIEW,
          OperationId.EXPORT_ATTESTATION_RECEIPT,
          OperationId.VERIFY_RECEIPT,
          OperationId.TRIAL_BALANCE,
          OperationId.PERIOD_SUMMARY,
          OperationId.FINANCIAL_POSITION,
          OperationId.INCOME_STATEMENT,
          OperationId.CHANGES_IN_EQUITY,
          OperationId.ACCOUNT_BALANCE,
          OperationId.ACCOUNT_LEDGER);

  private MachineContractDomainDescriptors() {}

  static ContractResponse.BookModelDescriptor bookModel() {
    BookModelFacts bookModel = ProtocolCatalog.domain().bookModel();
    return new ContractResponse.BookModelDescriptor(
        bookModel.boundary(),
        bookModel.entityScope(),
        bookModel.filesystem(),
        bookModel.credential(),
        bookModel.initialization(),
        bookModel.accountRegistry(),
        bookModel.currencyScope());
  }

  static ContractResponse.BookkeepingKernelDescriptor bookkeepingKernel() {
    dev.erst.fingrind.contract.protocol.BookkeepingKernelFacts kernel =
        ProtocolCatalog.domain().bookkeepingKernel();
    return new ContractResponse.BookkeepingKernelDescriptor(
        kernel.scope(),
        kernel.builtInStatements(),
        kernel.reportCapabilities(),
        kernel.description());
  }

  static List<CommandDescriptor> commandDescriptors() {
    return ProtocolCatalog.operations().stream()
        .map(MachineContractDomainDescriptors::commandDescriptor)
        .toList();
  }

  static CommandCatalogDescriptor commandCatalog() {
    return new CommandCatalogDescriptor(
        commandDescriptors(OperationCategory.DISCOVERY),
        commandDescriptors(OperationCategory.ADMINISTRATION),
        commandDescriptors(OperationCategory.QUERY),
        commandDescriptors(OperationCategory.WRITE));
  }

  static List<ExitCodeDescriptor> exitCodes() {
    return List.of(
        new ExitCodeDescriptor(0, "successful command"),
        new ExitCodeDescriptor(1, "invalid invocation or malformed request"),
        new ExitCodeDescriptor(
            2,
            "deterministic refusal after the command was understood, including unsupported output selections"),
        new ExitCodeDescriptor(
            3,
            "valid "
                + ProtocolCatalog.operationName(OperationId.EXECUTE_PLAN)
                + " request whose assertion step failed"),
        new ExitCodeDescriptor(4, "runtime failure while executing an otherwise valid invocation"),
        new ExitCodeDescriptor(
            5, "interactive prompt or managed runtime environment precondition failure"),
        new ExitCodeDescriptor(
            6, "protected-book path, passphrase, key-file, or verification failure"),
        new ExitCodeDescriptor(
            7, "protected-book maintenance precondition or destination-collision failure"),
        new ExitCodeDescriptor(70, "internal software defect"));
  }

  static List<ExitCodeDescriptor> exitCodes(OperationId operationId) {
    Set<Integer> allowedExitCodes = allowedExitCodes(operationId);
    return exitCodes().stream()
        .filter(exitCode -> allowedExitCodes.contains(exitCode.code()))
        .toList();
  }

  private static Set<Integer> allowedExitCodes(OperationId operationId) {
    if (TEMPLATE_ONLY_OPERATIONS.contains(operationId)) {
      return TEMPLATE_EXIT_CODES;
    }
    if (DISCOVERY_OPERATIONS.contains(operationId)) {
      return DISCOVERY_EXIT_CODES;
    }
    if (operationId == OperationId.GENERATE_BOOK_KEY_FILE
        || operationId == OperationId.GENERATE_ATTESTATION_KEY_FILE) {
      return KEY_GENERATION_EXIT_CODES;
    }
    if (MAINTENANCE_OPERATIONS.contains(operationId)) {
      return MAINTENANCE_EXIT_CODES;
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return EXECUTE_PLAN_EXIT_CODES;
    }
    return DEFAULT_MUTATION_EXIT_CODES;
  }

  static ContractResponse.AuditDescriptor audit() {
    return new ContractResponse.AuditDescriptor(
        List.of(
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.COMMAND_ID,
                "Caller-generated command identity for this request."),
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.IDEMPOTENCY_KEY,
                "Book-local idempotency key supplied by the caller."),
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.CAUSATION_ID,
                "Caller-supplied causation identifier."),
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.CORRELATION_ID,
                "Optional caller-supplied correlation identifier.")),
        List.of(
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.RECORDED_AT,
                "Commit timestamp generated by FinGrind at durable write time."),
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.SOURCE_CHANNEL,
                "Committed request channel generated by FinGrind.")));
  }

  static ContractResponse.AccountRegistryDescriptor accountRegistry() {
    return new ContractResponse.AccountRegistryDescriptor(
        ContractResponse.InitializationRequirement.REQUIRES_OPEN_BOOK,
        "redeclaration may update the display name and reactivate an inactive account, but will not amend accountType or account taxonomy",
        List.of(
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.ACCOUNT_CODE, "Book-local account code to declare."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.ACCOUNT_NAME,
                "Non-blank display name for the account."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.ACCOUNT_TYPE,
                "Account classification that determines normal-balance doctrine and which statement taxonomy family the account must join."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.ACCOUNT_NODE_KIND,
                "Chart node kind that determines whether the account is a hierarchy header or a direct posting target."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.PARENT_ACCOUNT_CODE,
                "Optional parent account code that places the account in the declared chart hierarchy."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION,
                "Required for ASSET, LIABILITY, and EQUITY accounts. Declares the account's financial position taxonomy."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.CASH_FLOW_ASSET_CLASSIFICATION,
                "Required for ASSET accounts and forbidden for every non-ASSET account. Declares whether the asset is cash and cash equivalents or one non-cash asset."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.UNIT_OF_MEASURE,
                "Required when financialPositionLineClassification is INVENTORY and forbidden for every non-inventory account. Declares the inventory account's unit token and exact quantity scale."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION,
                "Required for REVENUE and EXPENSE accounts. Declares the account's profit-and-loss taxonomy.")),
        List.of(
            new ContractResponse.FieldDescriptor(
                "accountCode", "Declared book-local account code."),
            new ContractResponse.FieldDescriptor(
                "accountName", "Current display name of the account."),
            new ContractResponse.FieldDescriptor("accountType", "Declared account classification."),
            new ContractResponse.FieldDescriptor("accountNodeKind", "Declared chart node kind."),
            new ContractResponse.FieldDescriptor(
                "parentAccountCode", "Declared optional chart parent account code."),
            new ContractResponse.FieldDescriptor(
                "financialPositionLineClassification",
                "Declared financial position taxonomy classification when the account belongs to the balance sheet."),
            new ContractResponse.FieldDescriptor(
                "cashFlowAssetClassification",
                "Declared cash-flow asset classification when the account belongs to ASSET accounts."),
            new ContractResponse.FieldDescriptor(
                "profitAndLossLineClassification",
                "Declared profit-and-loss taxonomy classification when the account belongs to the income statement."),
            new ContractResponse.FieldDescriptor(
                "unitOfMeasure",
                "Declared inventory unit-of-measure payload when the account belongs to inventory."),
            new ContractResponse.FieldDescriptor("normalBalance", "Declared normal balance side."),
            new ContractResponse.FieldDescriptor(
                "active", "Whether the account currently accepts postings."),
            new ContractResponse.FieldDescriptor(
                "declaredAt", "Clock timestamp of the first declaration.")),
        List.of(
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "accountType", AccountType.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "accountNodeKind", AccountNodeKind.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "financialPositionLineClassification",
                FinancialPositionLineClassification.declaredAccountWireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "profitAndLossLineClassification", ProfitAndLossLineClassification.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "cashFlowAssetClassification", CashFlowAssetClassification.wireValues())));
  }

  static ContractResponse.ReversalDescriptor reversals() {
    return new ContractResponse.ReversalDescriptor(
        "reversal-only",
        List.of(
            "book-must-be-initialized",
            "every-line-account-must-be-declared-and-active",
            "target-must-exist-in-book",
            "reversal-object-must-carry-plain-language-reason",
            "one-reversal-per-target",
            "reversal-must-negate-target"));
  }

  static ContractResponse.PreflightDescriptor preflight() {
    dev.erst.fingrind.contract.protocol.PreflightFacts preflight =
        ProtocolCatalog.domain().preflight();
    return new ContractResponse.PreflightDescriptor(
        preflight.semantics(),
        ContractResponse.CommitGuarantee.fromGuaranteed(preflight.commitGuarantee()),
        preflight.description());
  }

  static ContractResponse.CurrencyDescriptor currencyModel() {
    CurrencyFacts currency = ProtocolCatalog.domain().currency();
    return new ContractResponse.CurrencyDescriptor(
        currency.scope(), currency.multiCurrencyStatus(), currency.description());
  }

  static ContractResponse.PlanExecutionDescriptor planExecution() {
    PlanExecutionFacts facts = ProtocolCatalog.domain().planExecution();
    return new ContractResponse.PlanExecutionDescriptor(
        facts.transactionMode(), facts.failurePolicy(), facts.journal(), facts.hardLimitations());
  }

  private static CommandDescriptor commandDescriptor(ProtocolOperation operation) {
    return new CommandDescriptor(
        operation.id(),
        operation.aliases(),
        operation.options(),
        operation.executionMode(),
        operation.outputModes(),
        operation.artifactOutputs().stream()
            .map(
                artifact ->
                    new ArtifactOutputDescriptor(
                        artifact.format(), artifact.option(), artifact.description()))
            .toList(),
        operation.analysisSummary());
  }

  private static List<CommandDescriptor> commandDescriptors(OperationCategory category) {
    return ProtocolCatalog.operations().stream()
        .filter(operation -> operation.category() == category)
        .map(MachineContractDomainDescriptors::commandDescriptor)
        .toList();
  }
}
