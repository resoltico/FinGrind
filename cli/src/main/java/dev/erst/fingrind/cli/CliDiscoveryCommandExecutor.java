package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates.LedgerPlanTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates.PostingRequestTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestShapesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareAccountTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareTaxRegistrationTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.PlanTemplateTopic;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolRequestTemplateTopics;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Executes discovery and template commands that render contract-owned CLI metadata. */
final class CliDiscoveryCommandExecutor {
  private final CliDiscoveryResponseWriter responseWriter;
  private final CliMetadata metadata;

  CliDiscoveryCommandExecutor(CliDiscoveryResponseWriter responseWriter, CliMetadata metadata) {
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.metadata = Objects.requireNonNull(metadata, "metadata");
  }

  int writeHelp(
      @Nullable OperationId commandTopic,
      OutputMode outputMode,
      DiscoveryDetail detail,
      @Nullable OperationCategory category,
      boolean terseTopLevel) {
    EnvironmentDescriptor environmentDescriptor = environmentDescriptor();
    responseWriter.writeHelp(
        launcherAwareHelp(
            MachineContract.help(applicationIdentity(), environmentDescriptor, commandTopic)),
        environmentDescriptor,
        outputMode,
        detail,
        category,
        terseTopLevel);
    return 0;
  }

  int writeCapabilities(
      OutputMode outputMode, DiscoveryDetail detail, CliDiscoverySelections selections) {
    responseWriter.writeCapabilities(
        MachineContract.capabilities(applicationIdentity()), outputMode, detail, selections);
    return 0;
  }

  int writeEnvironment(OutputMode outputMode) {
    responseWriter.writeEnvironment(environmentDescriptor(), outputMode);
    return 0;
  }

  int writeVersion(OutputMode outputMode) {
    responseWriter.writeVersion(MachineContract.version(applicationIdentity()), outputMode);
    return 0;
  }

  int writeRequestTemplate() {
    return writeRequestTemplate(null, null);
  }

  int writeRequestTemplate(
      @Nullable OperationId commandTopic, @Nullable BookTemplateId bookTemplateId) {
    responseWriter.writeRawTemplate(requestTemplateFor(commandTopic, bookTemplateId));
    return 0;
  }

  int writePlanTemplate(PlanTemplateTopic topic) {
    responseWriter.writeRawTemplate(MachineContract.planTemplate(topic));
    return 0;
  }

  private ApplicationIdentity applicationIdentity() {
    return CliRuntimeContractDescriptors.applicationIdentity(metadata);
  }

  private static HelpDescriptor launcherAwareHelp(HelpDescriptor helpDescriptor) {
    List<String> usage =
        helpDescriptor.usage().stream().map(CliInvocationText::rewriteInvocationPrefix).toList();
    @Nullable RequestShapesDescriptor requestShapes = helpDescriptor.requestShapes();
    @Nullable PostingRequestTemplateDescriptor requestTemplate = helpDescriptor.requestTemplate();
    @Nullable DeclareAccountTemplateDescriptor declareAccountTemplate =
        helpDescriptor.declareAccountTemplate();
    @Nullable DeclareTaxRegistrationTemplateDescriptor declareTaxRegistrationTemplate =
        helpDescriptor.declareTaxRegistrationTemplate();
    @Nullable LedgerPlanTemplateDescriptor planTemplate = helpDescriptor.planTemplate();
    return new HelpDescriptor(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.protocolVersion(),
        helpDescriptor.description(),
        usage,
        helpDescriptor.bookModel(),
        helpDescriptor.bookkeepingKernel(),
        requestShapes,
        requestTemplate,
        declareAccountTemplate,
        declareTaxRegistrationTemplate,
        planTemplate,
        helpDescriptor.commands(),
        helpDescriptor.quickStart(),
        helpDescriptor.exitCodes(),
        helpDescriptor.preflight(),
        helpDescriptor.currencyModel());
  }

  private EnvironmentDescriptor environmentDescriptor() {
    return CliRuntimeContractDescriptors.environmentDescriptor(
        SqliteRuntime.probe(),
        FinGrindCli.runtimeDistribution(),
        FinGrindCli.runtimeBundleTarget());
  }

  static Object requestTemplateFor(
      @Nullable OperationId commandTopic, @Nullable BookTemplateId bookTemplateId) {
    if (commandTopic == null) {
      return MachineContract.requestTemplate(bookTemplateId);
    }
    if (commandTopic == OperationId.DECLARE_TAX_REGISTRATION) {
      return MachineContract.declareTaxRegistrationTemplate();
    }
    if (commandTopic == OperationId.DECLARE_ACCOUNT || commandTopic == OperationId.AMEND_ACCOUNT) {
      return MachineContract.declareAccountTemplate();
    }
    if (commandTopic == OperationId.RETIRE_ACCOUNT) {
      return MachineContract.retireAccountTemplate();
    }
    if (commandTopic == OperationId.ENROLL_KEY
        || commandTopic == OperationId.ROLLOVER_KEY
        || commandTopic == OperationId.REVOKE_KEY
        || commandTopic == OperationId.ALTER_POLICY) {
      return MachineContract.attestationRegistryTemplate(commandTopic);
    }
    if (ProtocolRequestTemplateTopics.supports(commandTopic)) {
      return Objects.requireNonNull(
          MachineContract.requestTemplate(commandTopic, bookTemplateId),
          "Missing request template for " + commandTopic.wireName() + ".");
    }
    throw new IllegalArgumentException(
        "Request templates are available only for "
            + String.join(", ", ProtocolRequestTemplateTopics.topicNames())
            + ".");
  }
}
