package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.BookModelFacts;
import dev.erst.fingrind.contract.protocol.CurrencyFacts;
import dev.erst.fingrind.contract.protocol.ExtensionSurfaceFacts;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanExecutionFacts;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Builds the non-request/non-response descriptor families in the machine contract. */
final class MachineContractDomainDescriptors {
  private MachineContractDomainDescriptors() {}

  static ContractResponse.BookModelDescriptor bookModel() {
    BookModelFacts bookModel = ProtocolCatalog.bookModel();
    return new ContractResponse.BookModelDescriptor(
        bookModel.boundary(),
        bookModel.entityScope(),
        bookModel.filesystem(),
        bookModel.credential(),
        bookModel.initialization(),
        bookModel.accountRegistry(),
        bookModel.currencyScope());
  }

  static ContractResponse.AccountingBaselineDescriptor accountingBaseline() {
    dev.erst.fingrind.contract.protocol.AccountingBaselineFacts baseline =
        ProtocolCatalog.accountingBaseline();
    return new ContractResponse.AccountingBaselineDescriptor(
        baseline.scope(),
        baseline.currentTarget(),
        baseline.nextTarget(),
        baseline.doctrineSources(),
        baseline.builtInStatements(),
        baseline.deliberateExclusions(),
        baseline.nonClaims(),
        baseline.reportCapabilities(),
        baseline.requiredMissingCapabilities(),
        baseline.defaultPolicyPack(),
        baseline.standardsPosition(),
        baseline.reportingPosition(),
        baseline.chartModelPosition(),
        baseline.smallEntityPosition(),
        baseline.operationalPosition(),
        baseline.taxPosition(),
        baseline.organizationalPosition(),
        baseline.isoClarification());
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
        new ExitCodeDescriptor(2, "deterministic refusal after the command was understood"),
        new ExitCodeDescriptor(
            3,
            "valid "
                + ProtocolCatalog.operationName(OperationId.EXECUTE_PLAN)
                + " request whose assertion step failed"),
        new ExitCodeDescriptor(4, "runtime or environment failure"));
  }

  static ContractResponse.AuditDescriptor audit() {
    return new ContractResponse.AuditDescriptor(
        List.of(
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.ACTOR_ID,
                "Stable identifier of the actor that initiated the request."),
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.ACTOR_TYPE,
                "Actor classification from the live actorType enum vocabulary."),
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
        "redeclaration may update the display name and reactivate an inactive account, but will not amend accountType, accountRole, or account taxonomy",
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
                ProtocolDeclareAccountFields.ACCOUNT_ROLE,
                "Doctrinal account role that determines whether the account is ordinary or contra."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.PARENT_ACCOUNT_CODE,
                "Optional parent account code that places the account in the declared chart hierarchy."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION,
                "Required for ASSET, LIABILITY, and EQUITY accounts. Declares the account's financial position taxonomy."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION,
                "Required for REVENUE and EXPENSE accounts. Declares the account's profit-and-loss taxonomy.")),
        List.of(
            new ContractResponse.FieldDescriptor(
                "accountCode", "Declared book-local account code."),
            new ContractResponse.FieldDescriptor(
                "accountName", "Current display name of the account."),
            new ContractResponse.FieldDescriptor("accountType", "Declared account classification."),
            new ContractResponse.FieldDescriptor("accountRole", "Declared account doctrinal role."),
            new ContractResponse.FieldDescriptor(
                "parentAccountCode", "Declared optional chart parent account code."),
            new ContractResponse.FieldDescriptor(
                "financialPositionLineClassification",
                "Declared financial position taxonomy classification when the account belongs to the balance sheet."),
            new ContractResponse.FieldDescriptor(
                "profitAndLossLineClassification",
                "Declared profit-and-loss taxonomy classification when the account belongs to the income statement."),
            new ContractResponse.FieldDescriptor("normalBalance", "Declared normal balance side."),
            new ContractResponse.FieldDescriptor(
                "active", "Whether the account currently accepts postings."),
            new ContractResponse.FieldDescriptor(
                "declaredAt", "Clock timestamp of the first declaration.")),
        List.of(
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "accountType", AccountType.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "accountRole", AccountRole.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "financialPositionLineClassification",
                FinancialPositionLineClassification.declaredAccountWireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "profitAndLossLineClassification", ProfitAndLossLineClassification.wireValues())));
  }

  static ContractResponse.ReversalDescriptor reversals() {
    return new ContractResponse.ReversalDescriptor(
        "reversal-only",
        List.of(
            "book-must-be-initialized",
            "every-line-account-must-be-declared-and-active",
            "target-must-exist-in-book",
            "reversal-object-must-carry-human-readable-reason",
            "one-reversal-per-target",
            "reversal-must-negate-target"));
  }

  static ContractResponse.PreflightDescriptor preflight() {
    dev.erst.fingrind.contract.protocol.PreflightFacts preflight = ProtocolCatalog.preflight();
    return new ContractResponse.PreflightDescriptor(
        preflight.semantics(),
        ContractResponse.CommitGuarantee.fromGuaranteed(preflight.commitGuarantee()),
        preflight.description());
  }

  static ContractResponse.CurrencyDescriptor currencyModel() {
    CurrencyFacts currency = ProtocolCatalog.currency();
    return new ContractResponse.CurrencyDescriptor(
        currency.scope(), currency.multiCurrencyStatus(), currency.description());
  }

  static ContractResponse.ExtensionSurfaceDescriptor extensionSurface() {
    ExtensionSurfaceFacts extensionSurface = ProtocolCatalog.extensionSurface();
    return new ContractResponse.ExtensionSurfaceDescriptor(
        extensionSurface.model(),
        extensionSurface.defaultPolicyPackId(),
        extensionSurface.implementedSeams(),
        extensionSurface.policySeams(),
        extensionSurface.futureContexts(),
        extensionSurface.description());
  }

  static ContractResponse.PlanExecutionDescriptor planExecution() {
    PlanExecutionFacts facts = ProtocolCatalog.planExecution();
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
        selectableOutputDefaults(operation),
        operation.artifactOutputs().stream()
            .map(
                artifact ->
                    new ArtifactOutputDescriptor(
                        artifact.format(), artifact.option(), artifact.description()))
            .toList(),
        operation.analysisSummary());
  }

  private static @Nullable SelectableOutputDefaultsDescriptor selectableOutputDefaults(
      ProtocolOperation operation) {
    if (operation.outputModes().isEmpty()) {
      return null;
    }
    return new SelectableOutputDefaultsDescriptor(
        OutputMode.HUMAN,
        operation.category() == OperationCategory.DISCOVERY ? OutputMode.HUMAN : OutputMode.JSON);
  }

  private static List<CommandDescriptor> commandDescriptors(OperationCategory category) {
    return ProtocolCatalog.operations().stream()
        .filter(operation -> operation.category() == category)
        .map(MachineContractDomainDescriptors::commandDescriptor)
        .toList();
  }
}
