package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import java.io.PrintStream;

/** Focused test fixture for the discovery response writer. */
final class CliDiscoveryResponseWriterFixture {
  private final CliDiscoveryResponseWriter writer;

  CliDiscoveryResponseWriterFixture(PrintStream outputStream) {
    writer = new CliDiscoveryResponseWriter(CliTestOutputChannels.forOutput(outputStream));
  }

  void writeHelp(HelpDescriptor helpDescriptor) {
    writeHelp(helpDescriptor, OutputMode.JSON, DiscoveryDetail.MINIMAL);
  }

  void writeHelp(HelpDescriptor helpDescriptor, OutputMode outputMode) {
    writeHelp(helpDescriptor, outputMode, DiscoveryDetail.MINIMAL);
  }

  void writeHelp(HelpDescriptor helpDescriptor, OutputMode outputMode, DiscoveryDetail detail) {
    writer.writeHelp(
        helpDescriptor, CliDiscoveryTestSupport.environment(), outputMode, detail, null, false);
  }

  void writeCapabilities(CapabilitiesDescriptor capabilitiesDescriptor) {
    writeCapabilities(capabilitiesDescriptor, OutputMode.JSON, DiscoveryDetail.MINIMAL);
  }

  void writeCapabilities(CapabilitiesDescriptor capabilitiesDescriptor, OutputMode outputMode) {
    writeCapabilities(capabilitiesDescriptor, outputMode, DiscoveryDetail.MINIMAL);
  }

  void writeCapabilities(
      CapabilitiesDescriptor capabilitiesDescriptor,
      OutputMode outputMode,
      DiscoveryDetail detail) {
    writer.writeCapabilities(
        capabilitiesDescriptor, outputMode, detail, CliDiscoverySelections.overview());
  }

  void writeEnvironment(EnvironmentDescriptor environmentDescriptor, OutputMode outputMode) {
    writer.writeEnvironment(environmentDescriptor, outputMode);
  }

  void writeVersion(VersionDescriptor versionDescriptor) {
    writeVersion(versionDescriptor, OutputMode.JSON);
  }

  void writeVersion(VersionDescriptor versionDescriptor, OutputMode outputMode) {
    writer.writeVersion(versionDescriptor, outputMode);
  }

  void writeRequestTemplate(
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor requestTemplate) {
    writer.writeRawTemplate(requestTemplate);
  }

  void writePlanTemplate(ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate) {
    writer.writeRawTemplate(planTemplate);
  }
}
