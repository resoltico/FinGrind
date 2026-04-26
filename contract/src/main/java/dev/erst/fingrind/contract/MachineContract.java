package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Canonical machine-readable contract assembler for the FinGrind CLI surface. */
public final class MachineContract {
  private static final String TEMPLATE_EFFECTIVE_DATE = "2026-04-17";

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
        ProtocolCatalog.operations().stream().map(ProtocolOperation::usage).toList(),
        MachineContractDescriptors.bookModel(),
        MachineContractDescriptors.commandDescriptors(),
        ProtocolCatalog.operations().stream()
            .flatMap(operation -> operation.examples().stream())
            .toList(),
        MachineContractDescriptors.exitCodes(),
        MachineContractDescriptors.preflight(),
        MachineContractDescriptors.currencyModel(),
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
        MachineContractDescriptors.commandCatalog(),
        MachineContractDescriptors.requestInput(),
        MachineContractDescriptors.requestShapes(),
        MachineContractDescriptors.responseModel(),
        MachineContractDescriptors.planExecution(),
        MachineContractDescriptors.audit(),
        MachineContractDescriptors.accountRegistry(),
        MachineContractDescriptors.reversals(),
        MachineContractDescriptors.preflight(),
        MachineContractDescriptors.currencyModel(),
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
        TEMPLATE_EFFECTIVE_DATE,
        List.of(
            new ContractTemplates.JournalLineTemplateDescriptor(
                "1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
            new ContractTemplates.JournalLineTemplateDescriptor(
                "2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")),
        new ContractTemplates.ProvenanceTemplateDescriptor(
            "operator-1", ActorType.HUMAN, "command-1", "idem-1", "cause-1", null),
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
}
