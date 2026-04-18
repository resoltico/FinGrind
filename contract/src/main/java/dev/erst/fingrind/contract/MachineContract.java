package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Canonical machine-readable contract assembler for the FinGrind CLI surface. */
public final class MachineContract {
  private MachineContract() {}

  /** Builds the canonical help descriptor. */
  public static ContractDiscovery.HelpDescriptor help(
      ContractDiscovery.ApplicationIdentity identity,
      ContractDiscovery.EnvironmentDescriptor environment) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(environment, "environment");
    return new ContractDiscovery.HelpDescriptor(
        identity.application(),
        identity.version(),
        identity.description(),
        ProtocolCatalog.operations().stream().map(ProtocolOperation::usage).toList(),
        MachineContractSupport.bookModel(),
        MachineContractSupport.commandDescriptors(),
        ProtocolCatalog.operations().stream()
            .flatMap(operation -> operation.examples().stream())
            .toList(),
        MachineContractSupport.exitCodes(),
        MachineContractSupport.preflight(),
        MachineContractSupport.currencyModel(),
        environment);
  }

  /** Builds the canonical capabilities descriptor. */
  public static ContractDiscovery.CapabilitiesDescriptor capabilities(
      ContractDiscovery.ApplicationIdentity identity,
      ContractDiscovery.EnvironmentDescriptor environment,
      Instant timestamp) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(environment, "environment");
    Objects.requireNonNull(timestamp, "timestamp");
    return new ContractDiscovery.CapabilitiesDescriptor(
        identity.application(),
        identity.version(),
        ProtocolCatalog.storageEngines(),
        "single-sqlite-file",
        ProtocolCatalog.operationNames(OperationCategory.DISCOVERY),
        ProtocolCatalog.operationNames(OperationCategory.ADMINISTRATION),
        ProtocolCatalog.operationNames(OperationCategory.QUERY),
        ProtocolCatalog.operationNames(OperationCategory.WRITE),
        MachineContractSupport.requestInput(),
        MachineContractSupport.requestShapes(),
        MachineContractSupport.responseModel(),
        MachineContractSupport.planExecution(),
        MachineContractSupport.audit(),
        MachineContractSupport.accountRegistry(),
        MachineContractSupport.reversals(),
        MachineContractSupport.preflight().semantics(),
        MachineContractSupport.preflight(),
        MachineContractSupport.currencyModel(),
        environment,
        timestamp.toString());
  }

  /** Builds the canonical version descriptor. */
  public static ContractDiscovery.VersionDescriptor version(
      ContractDiscovery.ApplicationIdentity identity) {
    Objects.requireNonNull(identity, "identity");
    return new ContractDiscovery.VersionDescriptor(
        identity.application(), identity.version(), identity.description());
  }

  /** Builds the canonical minimal posting-request template descriptor. */
  public static ContractTemplates.PostingRequestTemplateDescriptor requestTemplate(Clock clock) {
    Objects.requireNonNull(clock, "clock");
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        LocalDate.now(clock).toString(),
        List.of(
            new ContractTemplates.JournalLineTemplateDescriptor(
                "1000", JournalLine.EntrySide.DEBIT.wireValue(), "EUR", "10.00"),
            new ContractTemplates.JournalLineTemplateDescriptor(
                "2000", JournalLine.EntrySide.CREDIT.wireValue(), "EUR", "10.00")),
        new ContractTemplates.ProvenanceTemplateDescriptor(
            "operator-1", ActorType.USER.wireValue(), "command-1", "idem-1", "cause-1", null),
        null);
  }

  /** Builds the canonical minimal AI-agent ledger-plan template descriptor. */
  public static ContractTemplates.LedgerPlanTemplateDescriptor planTemplate(Clock clock) {
    Objects.requireNonNull(clock, "clock");
    return new ContractTemplates.LedgerPlanTemplateDescriptor(
        "plan-1",
        List.of(
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "initialize-book", LedgerStepKind.OPEN_BOOK.wireValue(), null, null, null, null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "declare-cash",
                LedgerStepKind.DECLARE_ACCOUNT.wireValue(),
                null,
                new ContractTemplates.DeclareAccountTemplateDescriptor(
                    "1000", "Cash", NormalBalance.DEBIT.wireValue()),
                null,
                null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "declare-revenue",
                LedgerStepKind.DECLARE_ACCOUNT.wireValue(),
                null,
                new ContractTemplates.DeclareAccountTemplateDescriptor(
                    "2000", "Revenue", NormalBalance.CREDIT.wireValue()),
                null,
                null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "post-journal",
                LedgerStepKind.POST_ENTRY.wireValue(),
                requestTemplate(clock),
                null,
                null,
                null),
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "assert-cash-balance",
                LedgerStepKind.ASSERT.wireValue(),
                null,
                null,
                new ContractTemplates.LedgerAssertionTemplateDescriptor(
                    LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS.wireValue(),
                    "1000",
                    null,
                    null,
                    "EUR",
                    "10.00",
                    NormalBalance.DEBIT.wireValue(),
                    null),
                null)));
  }
}
