package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.PdfReportCapabilityDescriptorProjection;
import java.util.List;

/** Derives the operator-facing PDF-capable report summary from command descriptors. */
final class CliDiscoveryPdfCapabilitySummary {
  private static final String PDF_OPTION = "--pdf-out <path>";

  private CliDiscoveryPdfCapabilitySummary() {}

  static String render(CapabilitiesDescriptor capabilitiesDescriptor) {
    return renderOperationWireNames(
        PdfReportCapabilityDescriptorProjection.pdfReportOperationWireNames(
            capabilitiesDescriptor));
  }

  static String render(List<CommandDescriptor> queryReportCommands) {
    return renderOperationWireNames(
        PdfReportCapabilityDescriptorProjection.pdfReportOperationWireNames(queryReportCommands));
  }

  private static String renderOperationWireNames(List<String> operationIds) {
    return switch (operationIds.size()) {
      case 0 -> "No report commands can emit pdf via " + PDF_OPTION + ".";
      case 1 -> operationIds.getFirst() + " can emit pdf via " + PDF_OPTION + ".";
      case 2 ->
          operationIds.get(0)
              + " and "
              + operationIds.get(1)
              + " can emit pdf via "
              + PDF_OPTION
              + ".";
      default ->
          String.join(", ", operationIds.subList(0, operationIds.size() - 1))
              + ", and "
              + operationIds.getLast()
              + " can emit pdf via "
              + PDF_OPTION
              + ".";
    };
  }
}
