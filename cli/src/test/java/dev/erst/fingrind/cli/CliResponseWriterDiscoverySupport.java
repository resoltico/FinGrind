package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import java.io.PrintStream;

/** Discovery and failure portion of the split test-only response writer compatibility chain. */
class CliResponseWriterDiscoverySupport extends CliResponseWriterBaseSupport {
  CliResponseWriterDiscoverySupport(PrintStream outputStream) {
    super(outputStream);
  }

  CliResponseWriterDiscoverySupport(PrintStream outputStream, PrintStream diagnosticsStream) {
    super(outputStream, diagnosticsStream);
  }

  void writeHelp(HelpDescriptor helpDescriptor) {
    writeHelp(helpDescriptor, OutputMode.JSON, DiscoveryDetail.MINIMAL);
  }

  void writeHelp(HelpDescriptor helpDescriptor, OutputMode outputMode) {
    writeHelp(helpDescriptor, outputMode, DiscoveryDetail.MINIMAL);
  }

  void writeHelp(HelpDescriptor helpDescriptor, OutputMode outputMode, DiscoveryDetail detail) {
    discoveryWriter.writeHelp(
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
    discoveryWriter.writeCapabilities(
        capabilitiesDescriptor, outputMode, detail, CliDiscoverySelections.overview());
  }

  void writeEnvironment(EnvironmentDescriptor environmentDescriptor, OutputMode outputMode) {
    discoveryWriter.writeEnvironment(environmentDescriptor, outputMode);
  }

  void writeVersion(VersionDescriptor versionDescriptor) {
    writeVersion(versionDescriptor, OutputMode.JSON);
  }

  void writeVersion(VersionDescriptor versionDescriptor, OutputMode outputMode) {
    discoveryWriter.writeVersion(versionDescriptor, outputMode);
  }

  void writeRequestTemplate(ContractTemplates.PostingRequestTemplateDescriptor requestTemplate) {
    discoveryWriter.writeRawTemplate(requestTemplate);
  }

  void writePlanTemplate(ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate) {
    discoveryWriter.writeRawTemplate(planTemplate);
  }

  void writeRawTemplate(Object template) {
    discoveryWriter.writeRawTemplate(template);
  }

  void writeFailure(CliFailure failure) {
    failureWriter.writeFailure(failure, OutputMode.JSON);
  }

  void writeFailure(String code, String message) {
    writeFailure(new CliFailure(code, message, null, null));
  }
}
