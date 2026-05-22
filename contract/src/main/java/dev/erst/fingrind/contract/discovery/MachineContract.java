package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical machine-readable contract assembler for the FinGrind CLI surface. */
public final class MachineContract {
  private MachineContract() {}

  /** Builds the canonical help descriptor. */
  public static HelpDescriptor help(
      ApplicationIdentity identity, EnvironmentDescriptor environment) {
    return help(identity, environment, null);
  }

  /** Builds the canonical help descriptor, optionally filtered to one command topic. */
  public static HelpDescriptor help(
      ApplicationIdentity identity,
      EnvironmentDescriptor environment,
      @Nullable OperationId commandTopic) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(environment, "environment");
    @Nullable ProtocolOperation selectedOperation =
        commandTopic == null ? null : ProtocolCatalog.operation(commandTopic);
    return new HelpDescriptor(
        identity.application(),
        identity.version(),
        identity.description(),
        selectedOperation == null
            ? ProtocolCatalog.operations().stream().map(ProtocolOperation::usage).toList()
            : List.of(selectedOperation.usage()),
        MachineContractDomainDescriptors.bookModel(),
        MachineContractDomainDescriptors.bookkeepingKernel(),
        MachineContractTemplatesCatalog.requestShapesFor(selectedOperation),
        postingRequestTemplateFor(selectedOperation),
        declareAccountTemplateFor(selectedOperation),
        ledgerPlanTemplateFor(selectedOperation),
        selectedOperation == null
            ? MachineContractDomainDescriptors.commandDescriptors()
            : MachineContractDomainDescriptors.commandDescriptors().stream()
                .filter(command -> command.name() == commandTopic)
                .toList(),
        selectedOperation == null
            ? MachineContractQuickStarts.canonicalQuickStart(
                environment.distribution().runtimeDistribution())
            : List.of(),
        MachineContractDomainDescriptors.exitCodes(),
        MachineContractDomainDescriptors.preflight(),
        MachineContractDomainDescriptors.currencyModel());
  }

  /** Builds the canonical capabilities descriptor. */
  public static CapabilitiesDescriptor capabilities(ApplicationIdentity identity) {
    Objects.requireNonNull(identity, "identity");
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
        MachineContractDomainDescriptors.bookkeepingKernel());
  }

  /** Builds the canonical version descriptor. */
  public static VersionDescriptor version(ApplicationIdentity identity) {
    Objects.requireNonNull(identity, "identity");
    return new VersionDescriptor(
        identity.application(), identity.version(), identity.description());
  }

  /** Builds the canonical minimal posting-request template descriptor. */
  public static ContractTemplates.PostingRequestTemplateDescriptor requestTemplate() {
    return MachineContractTemplatesCatalog.requestTemplate();
  }

  /** Builds the canonical minimal declare-account request template descriptor. */
  public static ContractTemplates.DeclareAccountTemplateDescriptor declareAccountTemplate() {
    return MachineContractTemplatesCatalog.declareAccountTemplate();
  }

  /** Builds the canonical minimal AI-agent ledger-plan template descriptor. */
  public static ContractTemplates.LedgerPlanTemplateDescriptor planTemplate() {
    return MachineContractTemplatesCatalog.planTemplate();
  }

  private static ContractTemplates.@Nullable PostingRequestTemplateDescriptor
      postingRequestTemplateFor(@Nullable ProtocolOperation selectedOperation) {
    return MachineContractTemplatesCatalog.postingRequestTemplateFor(selectedOperation);
  }

  private static ContractTemplates.@Nullable DeclareAccountTemplateDescriptor
      declareAccountTemplateFor(@Nullable ProtocolOperation selectedOperation) {
    return MachineContractTemplatesCatalog.declareAccountTemplateFor(selectedOperation);
  }

  private static ContractTemplates.@Nullable LedgerPlanTemplateDescriptor ledgerPlanTemplateFor(
      @Nullable ProtocolOperation selectedOperation) {
    return MachineContractTemplatesCatalog.ledgerPlanTemplateFor(selectedOperation);
  }
}
