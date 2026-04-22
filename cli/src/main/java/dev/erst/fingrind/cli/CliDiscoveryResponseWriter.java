package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.ContractTemplates;
import dev.erst.fingrind.contract.HelpDescriptor;
import dev.erst.fingrind.contract.VersionDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.Objects;

/** Renders discovery and template responses through the shared CLI output channel. */
final class CliDiscoveryResponseWriter {
  private final CliOutputChannel outputChannel;

  CliDiscoveryResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeHelp(HelpDescriptor helpDescriptor, OutputMode outputMode) {
    outputMode.run(
        () -> outputChannel.writePrettySuccess(helpDescriptor),
        () -> outputChannel.writeText(CliDiscoveryOutputRenderer.renderHelpHuman(helpDescriptor)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.HELP));
        });
  }

  void writeCapabilities(CapabilitiesDescriptor capabilitiesDescriptor, OutputMode outputMode) {
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

  void writeVersion(VersionDescriptor versionDescriptor, OutputMode outputMode) {
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
