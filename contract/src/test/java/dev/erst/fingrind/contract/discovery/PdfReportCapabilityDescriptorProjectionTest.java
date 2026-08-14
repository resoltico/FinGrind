package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract tests for the descriptor-only PDF report capability projection. */
class PdfReportCapabilityDescriptorProjectionTest {
  private static final ApplicationIdentity DOCUMENT_IDENTITY =
      new ApplicationIdentity("FinGrind", "documentation", "Descriptor projection test identity.");

  @Test
  void canonicalDescriptor_matchesTheProtocolOwnedPdfReportArtifactsInOrder() {
    List<String> expected =
        ProtocolCatalog.operations().stream()
            .filter(operation -> operation.category() == OperationCategory.QUERY)
            .filter(
                operation ->
                    operation.artifactOutputs().stream()
                        .anyMatch(
                            artifact ->
                                ProtocolArtifactOutput.pdfFormat().equals(artifact.format())))
            .map(operation -> operation.id().wireName())
            .toList();

    assertEquals(
        expected,
        PdfReportCapabilityDescriptorProjection.pdfReportOperationWireNames(
            MachineContract.capabilities(DOCUMENT_IDENTITY)));
  }

  @Test
  void projection_rejectsDuplicateQueryDescriptorsBeforeFilteringArtifacts() {
    CommandDescriptor queryReport =
        MachineContract.capabilities(DOCUMENT_IDENTITY).commands().query().stream()
            .filter(
                command ->
                    command.artifactOutputs().stream()
                        .anyMatch(
                            artifact ->
                                ProtocolArtifactOutput.pdfFormat().equals(artifact.format())))
            .findFirst()
            .orElseThrow();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PdfReportCapabilityDescriptorProjection.pdfReportOperationWireNames(
                    List.of(queryReport, queryReport)));

    assertEquals(
        "Duplicate query report descriptor in PDF capability discovery: "
            + queryReport.name().wireName(),
        exception.getMessage());
  }
}
