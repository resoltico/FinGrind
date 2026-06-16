package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates.LedgerPlanTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestShapesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareAccountTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.PostingRequestTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
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
    return writeRequestTemplate(null);
  }

  int writeRequestTemplate(@Nullable OperationId commandTopic) {
    responseWriter.writeRawTemplate(requestTemplateFor(commandTopic));
    return 0;
  }

  int writePlanTemplate() {
    responseWriter.writeRawTemplate(MachineContract.planTemplate());
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
    @Nullable LedgerPlanTemplateDescriptor planTemplate = helpDescriptor.planTemplate();
    return new HelpDescriptor(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.description(),
        usage,
        helpDescriptor.bookModel(),
        helpDescriptor.bookkeepingKernel(),
        requestShapes,
        requestTemplate,
        declareAccountTemplate,
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

  static Object requestTemplateFor(@Nullable OperationId commandTopic) {
    if (commandTopic == null
        || commandTopic == OperationId.POST_ENTRY
        || commandTopic == OperationId.PREFLIGHT_ENTRY) {
      return MachineContract.requestTemplate();
    }
    if (commandTopic == OperationId.DECLARE_ACCOUNT) {
      return MachineContract.declareAccountTemplate();
    }
    throw new IllegalArgumentException(
        "Request templates are available only for "
            + String.join(
                ", ",
                ProtocolCatalog.operationName(OperationId.POST_ENTRY),
                ProtocolCatalog.operationName(OperationId.PREFLIGHT_ENTRY),
                ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT))
            + ".");
  }
}
