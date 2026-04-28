package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Canonical machine-readable contract assembler for the FinGrind CLI surface. */
public final class MachineContract {
  private MachineContract() {}

  /** Builds the canonical help descriptor. */
  public static HelpDescriptor help(
      ApplicationIdentity identity, EnvironmentDescriptor environment) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(environment, "environment");
    return new HelpDescriptor(
        identity.application(),
        identity.version(),
        identity.description(),
        ProtocolCatalog.operations().stream().map(operation -> operation.usage()).toList(),
        MachineContractDomainDescriptors.bookModel(),
        MachineContractDomainDescriptors.commandDescriptors(),
        canonicalQuickStart(),
        MachineContractDomainDescriptors.exitCodes(),
        MachineContractDomainDescriptors.preflight(),
        MachineContractDomainDescriptors.currencyModel(),
        environment);
  }

  /** Builds the canonical capabilities descriptor. */
  public static CapabilitiesDescriptor capabilities(
      ApplicationIdentity identity, EnvironmentDescriptor environment, Instant timestamp) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(environment, "environment");
    Objects.requireNonNull(timestamp, "timestamp");
    return new CapabilitiesDescriptor(
        identity.application(),
        identity.version(),
        new StorageSurfaceDescriptor(ProtocolCatalog.storageEngines(), "single-sqlite-file"),
        MachineContractDomainDescriptors.commandCatalog(),
        MachineContractRequestInputDescriptors.requestInput(),
        MachineContractRequestShapeDescriptors.requestShapes(),
        MachineContractResponseDescriptors.responseModel(),
        MachineContractDomainDescriptors.planExecution(),
        MachineContractDomainDescriptors.audit(),
        MachineContractDomainDescriptors.accountRegistry(),
        MachineContractDomainDescriptors.reversals(),
        MachineContractDomainDescriptors.preflight(),
        MachineContractDomainDescriptors.currencyModel(),
        environment,
        timestamp.toString());
  }

  /** Builds the canonical version descriptor. */
  public static VersionDescriptor version(ApplicationIdentity identity) {
    Objects.requireNonNull(identity, "identity");
    return new VersionDescriptor(
        identity.application(), identity.version(), identity.description());
  }

  /** Builds the canonical minimal posting-request template descriptor. */
  public static ContractTemplates.PostingRequestTemplateDescriptor requestTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        ScaffoldPlaceholders.EFFECTIVE_DATE,
        List.of(
            new ContractTemplates.JournalLineTemplateDescriptor(
                "1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
            new ContractTemplates.JournalLineTemplateDescriptor(
                "2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")),
        new ContractTemplates.ProvenanceTemplateDescriptor(
            ScaffoldPlaceholders.ACTOR_ID,
            ActorType.AGENT,
            ScaffoldPlaceholders.COMMAND_ID,
            ScaffoldPlaceholders.IDEMPOTENCY_KEY,
            ScaffoldPlaceholders.CAUSATION_ID,
            null),
        null);
  }

  /** Builds the canonical minimal AI-agent ledger-plan template descriptor. */
  public static ContractTemplates.LedgerPlanTemplateDescriptor planTemplate() {
    return new ContractTemplates.LedgerPlanTemplateDescriptor(
        "plan-1",
        List.of(
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "initialize-book", LedgerStepKind.OPEN_BOOK, null, null, null, null, null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "declare-cash",
                LedgerStepKind.DECLARE_ACCOUNT,
                null,
                new ContractTemplates.DeclareAccountTemplateDescriptor(
                    "1000", "Cash", NormalBalance.DEBIT),
                null,
                null,
                null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "declare-revenue",
                LedgerStepKind.DECLARE_ACCOUNT,
                null,
                new ContractTemplates.DeclareAccountTemplateDescriptor(
                    "2000", "Revenue", NormalBalance.CREDIT),
                null,
                null,
                null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "post-journal",
                LedgerStepKind.POST_ENTRY,
                requestTemplate(),
                null,
                null,
                null,
                null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "assert-cash-balance",
                LedgerStepKind.ASSERT,
                null,
                null,
                null,
                new ContractTemplates.LedgerAssertionTemplateDescriptor(
                    LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
                    "1000",
                    null,
                    null,
                    "EUR",
                    "10.00",
                    BalanceSide.DEBIT,
                    null),
                null)));
  }

  private static List<WorkflowStepDescriptor> canonicalQuickStart() {
    return List.of(
        WorkflowStepDescriptor.command(
            "fingrind %s --book-key-file ./acme.book-key"
                .formatted(ProtocolCatalog.operationName(OperationId.GENERATE_BOOK_KEY_FILE))),
        WorkflowStepDescriptor.command(
            "fingrind %s --book-file ./acme.sqlite --book-key-file ./acme.book-key"
                .formatted(ProtocolCatalog.operationName(OperationId.OPEN_BOOK))),
        WorkflowStepDescriptor.edit(
            "Create ./declare-account-cash.json with {\"accountCode\":\"1000\",\"accountName\":\"Cash\",\"normalBalance\":\"DEBIT\"}."),
        WorkflowStepDescriptor.command(
            "fingrind %s --book-file ./acme.sqlite --book-key-file ./acme.book-key --request-file ./declare-account-cash.json"
                .formatted(ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT))),
        WorkflowStepDescriptor.edit(
            "Create ./declare-account-revenue.json with {\"accountCode\":\"2000\",\"accountName\":\"Revenue\",\"normalBalance\":\"CREDIT\"}."),
        WorkflowStepDescriptor.command(
            "fingrind %s --book-file ./acme.sqlite --book-key-file ./acme.book-key --request-file ./declare-account-revenue.json"
                .formatted(ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT))),
        WorkflowStepDescriptor.command(
            "fingrind %s > ./request.json"
                .formatted(ProtocolCatalog.operationName(OperationId.PRINT_REQUEST_TEMPLATE))),
        WorkflowStepDescriptor.edit(
            "Replace scaffold placeholders such as effectiveDate and every replace-before-commit-* provenance value in ./request.json before submitting the request."),
        WorkflowStepDescriptor.note(
            "Use a fresh provenance.idempotencyKey for each committed posting on the same book."),
        WorkflowStepDescriptor.command(
            "fingrind %s --book-file ./acme.sqlite --book-key-file ./acme.book-key --request-file ./request.json"
                .formatted(ProtocolCatalog.operationName(OperationId.PREFLIGHT_ENTRY))),
        WorkflowStepDescriptor.command(
            "fingrind %s --book-file ./acme.sqlite --book-key-file ./acme.book-key --request-file ./request.json"
                .formatted(ProtocolCatalog.operationName(OperationId.POST_ENTRY))),
        WorkflowStepDescriptor.command(
            "fingrind %s --book-file ./acme.sqlite --book-key-file ./acme.book-key --output human"
                .formatted(ProtocolCatalog.operationName(OperationId.TRIAL_BALANCE))));
  }
}
