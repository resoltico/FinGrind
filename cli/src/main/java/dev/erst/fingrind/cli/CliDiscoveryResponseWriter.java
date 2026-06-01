package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import java.util.Objects;

/** Renders discovery and template responses through the shared CLI output channel. */
final class CliDiscoveryResponseWriter {
  private final CliOutputChannel outputChannel;

  CliDiscoveryResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeHelp(
      HelpDescriptor helpDescriptor,
      OutputMode outputMode,
      DiscoveryDetail detail,
      @org.jspecify.annotations.Nullable OperationCategory category) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    CliDiscoveryPayloadMapper.helpPayload(helpDescriptor, detail, category))),
        () -> outputChannel.writeText(CliDiscoveryOutputRenderer.renderHelpText(helpDescriptor)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.HELP));
        });
  }

  void writeCapabilities(
      CapabilitiesDescriptor capabilitiesDescriptor,
      OutputMode outputMode,
      DiscoveryDetail detail,
      CliDiscoverySelections selections) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    CliDiscoveryPayloadMapper.capabilitiesPayloadAny(
                        capabilitiesDescriptor, detail, selections))),
        () ->
            outputChannel.writeText(
                CliDiscoveryOutputRenderer.renderCapabilitiesText(capabilitiesDescriptor)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.CAPABILITIES));
        });
  }

  void writeEnvironment(EnvironmentDescriptor environmentDescriptor, OutputMode outputMode) {
    outputMode.run(
        () -> outputChannel.writeEnvelope(CliEnvelopeMapper.successEnvelope(environmentDescriptor)),
        () ->
            outputChannel.writeText(
                CliDiscoveryOutputRenderer.renderEnvironmentText(environmentDescriptor)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.ENVIRONMENT));
        });
  }

  void writeVersion(VersionDescriptor versionDescriptor, OutputMode outputMode) {
    outputMode.run(
        () -> outputChannel.writeEnvelope(CliEnvelopeMapper.successEnvelope(versionDescriptor)),
        () ->
            outputChannel.writeText(
                CliDiscoveryOutputRenderer.renderVersionText(versionDescriptor)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.VERSION));
        });
  }

  void writeRawTemplate(Object template) {
    outputChannel.writePrettyJson(template);
  }
}
