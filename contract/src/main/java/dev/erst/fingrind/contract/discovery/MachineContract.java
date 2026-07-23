package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates.PostingRequestTemplateDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import dev.erst.fingrind.core.BookTemplateId;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical machine-readable contract assembler for the FinGrind CLI surface. */
public final class MachineContract {
  private static final String PROTOCOL_VERSION = "36";

  private MachineContract() {}

  /** Returns the current public machine-contract protocol version. */
  public static String protocolVersion() {
    return PROTOCOL_VERSION;
  }

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
        protocolVersion(),
        identity.description(),
        selectedOperation == null
            ? ProtocolCatalog.operations().stream().map(ProtocolOperation::usage).toList()
            : List.of(selectedOperation.usage()),
        MachineContractDomainDescriptors.bookModel(),
        MachineContractDomainDescriptors.bookkeepingKernel(),
        MachineContractTemplatesCatalog.requestShapesFor(selectedOperation),
        MachineContractTemplatesCatalog.postingRequestTemplateFor(selectedOperation, null),
        MachineContractTemplatesCatalog.declareAccountTemplateFor(selectedOperation),
        MachineContractTemplatesCatalog.declareTaxRegistrationTemplateFor(selectedOperation),
        MachineContractTemplatesCatalog.ledgerPlanTemplateFor(selectedOperation),
        selectedOperation == null
            ? MachineContractDomainDescriptors.commandDescriptors()
            : MachineContractDomainDescriptors.commandDescriptors().stream()
                .filter(command -> command.name() == commandTopic)
                .toList(),
        selectedOperation == null
            ? MachineContractQuickStarts.canonicalQuickStart(
                environment.runtime().runtimeDistribution())
            : List.of(),
        selectedOperation == null
            ? MachineContractDomainDescriptors.exitCodes()
            : MachineContractDomainDescriptors.exitCodes(selectedOperation.id()),
        MachineContractDomainDescriptors.preflight(),
        MachineContractDomainDescriptors.currencyModel());
  }

  /** Builds the canonical capabilities descriptor. */
  public static CapabilitiesDescriptor capabilities(ApplicationIdentity identity) {
    Objects.requireNonNull(identity, "identity");
    return new CapabilitiesDescriptor(
        identity.application(),
        identity.version(),
        protocolVersion(),
        new StorageSurfaceDescriptor(
            ProtocolCatalog.runtime().storageEngines(), "single-sqlite-file"),
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
        MachineContractDomainDescriptors.bookkeepingKernel(),
        ProtocolCatalog.domain().capabilities());
  }

  /** Builds the canonical version descriptor. */
  public static VersionDescriptor version(ApplicationIdentity identity) {
    Objects.requireNonNull(identity, "identity");
    return new VersionDescriptor(
        identity.application(), identity.version(), protocolVersion(), identity.description());
  }

  /** Builds the canonical minimal posting-request template descriptor. */
  public static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor requestTemplate() {
    return requestTemplate((BookTemplateId) null);
  }

  /** Builds the canonical minimal posting-request template descriptor for one doctrine. */
  public static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor requestTemplate(
      @Nullable BookTemplateId bookTemplateId) {
    return MachineContractTemplatesCatalog.requestTemplate(bookTemplateId);
  }

  /** Builds one command-specific posting-request template descriptor when the command owns one. */
  public static @Nullable PostingRequestTemplateDescriptor requestTemplate(
      OperationId commandTopic) {
    return requestTemplate(commandTopic, null);
  }

  /** Builds one command-specific posting-request template descriptor for one doctrine. */
  public static @Nullable PostingRequestTemplateDescriptor requestTemplate(
      OperationId commandTopic, @Nullable BookTemplateId bookTemplateId) {
    Objects.requireNonNull(commandTopic, "commandTopic");
    return MachineContractTemplatesCatalog.postingRequestTemplateFor(
        ProtocolCatalog.operation(commandTopic), bookTemplateId);
  }

  /** Builds the canonical minimal declare-account request template descriptor. */
  public static ContractTemplates.DeclareAccountTemplateDescriptor declareAccountTemplate() {
    return MachineContractTemplatesCatalog.declareAccountTemplate();
  }

  /** Builds the canonical minimal retire-account request template descriptor. */
  public static ContractTemplates.RetireAccountTemplateDescriptor retireAccountTemplate() {
    return MachineContractTemplatesCatalog.retireAccountTemplate();
  }

  /** Builds the canonical minimal declare-tax-registration request template descriptor. */
  public static ContractTemplates.DeclareTaxRegistrationTemplateDescriptor
      declareTaxRegistrationTemplate() {
    return MachineContractTemplatesCatalog.declareTaxRegistrationTemplate();
  }

  /** Builds the canonical minimal AI-agent ledger-plan template descriptor. */
  public static ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate() {
    return MachineContractTemplatesCatalog.planTemplate();
  }

  /** Builds a topic-specific executable ledger-plan scaffold. */
  public static ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate(
      PlanTemplateTopic topic) {
    return MachineContractTemplatesCatalog.planTemplate(topic);
  }

  /** Builds the canonical quick-start workflow for one published surface. */
  public static WorkflowDescriptor quickStart(WorkflowSurface surface) {
    Objects.requireNonNull(surface, "surface");
    return MachineContractQuickStarts.workflow(surface);
  }
}
