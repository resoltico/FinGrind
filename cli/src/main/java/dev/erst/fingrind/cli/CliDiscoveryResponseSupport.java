package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ContractDiscovery;
import dev.erst.fingrind.contract.ContractTemplates;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.Objects;

/** Renders discovery and template responses through the shared CLI output channel. */
final class CliDiscoveryResponseSupport {
  private final CliOutputChannel outputChannel;

  CliDiscoveryResponseSupport(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeHelp(ContractDiscovery.HelpDescriptor helpDescriptor, OutputMode outputMode) {
    outputMode.run(
        () -> outputChannel.writePrettySuccess(helpDescriptor),
        () -> outputChannel.writeText(CliDiscoveryOutputRenderer.renderHelpHuman(helpDescriptor)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.HELP));
        });
  }

  void writeCapabilities(
      ContractDiscovery.CapabilitiesDescriptor capabilitiesDescriptor, OutputMode outputMode) {
    outputMode.run(
        () -> outputChannel.writePrettySuccess(capabilitiesDescriptor),
        () ->
            outputChannel.writeText(
                CliDiscoveryOutputRenderer.renderCapabilitiesHuman(capabilitiesDescriptor)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.CAPABILITIES));
        });
  }

  void writeVersion(ContractDiscovery.VersionDescriptor versionDescriptor, OutputMode outputMode) {
    outputMode.run(
        () -> outputChannel.writePrettySuccess(versionDescriptor),
        () ->
            outputChannel.writeText(
                CliDiscoveryOutputRenderer.renderVersionHuman(versionDescriptor)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.VERSION));
        });
  }

  void writeRequestTemplate(ContractTemplates.PostingRequestTemplateDescriptor requestTemplate) {
    outputChannel.writePrettyJson(requestTemplate);
  }

  void writePlanTemplate(ContractTemplates.LedgerPlanTemplateDescriptor planTemplate) {
    outputChannel.writePrettyJson(planTemplate);
  }
}
