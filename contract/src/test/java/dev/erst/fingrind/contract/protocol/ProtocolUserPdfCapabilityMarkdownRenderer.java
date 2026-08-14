package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.PdfReportCapabilityDescriptorProjection;
import java.util.ArrayList;
import java.util.List;

/** Renders the descriptor-owned PDF-capable report inventory in the three user guides. */
final class ProtocolUserPdfCapabilityMarkdownRenderer {
  static final String PDF_REPORT_INVENTORY_BEGIN =
      "<!-- BEGIN GENERATED PDF-CAPABLE REPORT INVENTORY -->";
  static final String PDF_REPORT_INVENTORY_END =
      "<!-- END GENERATED PDF-CAPABLE REPORT INVENTORY -->";
  private static final ApplicationIdentity DOCUMENT_IDENTITY =
      new ApplicationIdentity("FinGrind", "documentation", "User-document descriptor projection.");

  private ProtocolUserPdfCapabilityMarkdownRenderer() {}

  static String pdfReportInventoryBlock() {
    List<String> lines = new ArrayList<>();
    lines.add(PDF_REPORT_INVENTORY_BEGIN);
    lines.add(
        "The following report commands can write one PDF artifact through `--pdf-out <path>`, in descriptor order:");
    lines.add("");
    PdfReportCapabilityDescriptorProjection.pdfReportOperationWireNames(
            MachineContract.capabilities(DOCUMENT_IDENTITY))
        .forEach(operationWireName -> lines.add("- `%s`".formatted(operationWireName)));
    lines.add(PDF_REPORT_INVENTORY_END);
    return String.join("\n", lines);
  }
}
