package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression tests for descriptor-owned user PDF capability documentation. */
class ProtocolUserPdfCapabilityDocumentSyncTest extends ProtocolContractRepositorySupport {
  @Test
  void generatedInventory_isTheCompleteGoldenDescriptorProjection() {
    assertEquals(
        """
        <!-- BEGIN GENERATED PDF-CAPABLE REPORT INVENTORY -->
        The following report commands can write one PDF artifact through `--pdf-out <path>`, in descriptor order:

        - `tax-obligation`
        - `account-balance`
        - `trial-balance`
        - `account-ledger`
        - `period-summary`
        - `financial-position`
        - `inventory-valuation`
        - `accrual-cutoff-schedule`
        - `fixed-asset-register`
        - `financing-register`
        - `realized-foreign-exchange-register`
        - `latvian-payroll-register`
        - `income-statement`
        - `cash-flow-statement`
        - `changes-in-equity`
        <!-- END GENERATED PDF-CAPABLE REPORT INVENTORY -->""",
        ProtocolUserPdfCapabilityMarkdownRenderer.pdfReportInventoryBlock());
  }

  @Test
  void checkedInUserGuides_matchTheGeneratedInventoryAndItsLayout() throws IOException {
    for (String relativeDocumentPath : ProtocolUserPdfCapabilityDocumentSync.DOCUMENT_PATHS) {
      String document = Files.readString(repositoryRoot().resolve(relativeDocumentPath));
      assertEquals(
          document,
          ProtocolUserPdfCapabilityDocumentSync.updatedDocument(document, relativeDocumentPath),
          relativeDocumentPath + " must retain the descriptor-owned PDF report inventory.");
    }
  }

  @Test
  void main_synchronizesEveryUserPdfGuide(@TempDir Path tempDir) throws IOException {
    Path docsDirectory = tempDir.resolve("docs");
    Files.createDirectories(docsDirectory);
    for (String relativeDocumentPath : ProtocolUserPdfCapabilityDocumentSync.DOCUMENT_PATHS) {
      Path documentPath = tempDir.resolve(relativeDocumentPath);
      Files.writeString(
          documentPath,
          "Header\n\n"
              + ProtocolUserPdfCapabilityMarkdownRenderer.PDF_REPORT_INVENTORY_BEGIN
              + "\nstale\n"
              + ProtocolUserPdfCapabilityMarkdownRenderer.PDF_REPORT_INVENTORY_END
              + "\n");
    }

    ProtocolUserPdfCapabilityDocumentSyncMain.main(new String[] {tempDir.toString()});

    for (String relativeDocumentPath : ProtocolUserPdfCapabilityDocumentSync.DOCUMENT_PATHS) {
      String document = Files.readString(tempDir.resolve(relativeDocumentPath));
      assertTrue(document.contains("`changes-in-equity`"), relativeDocumentPath);
      assertEquals(
          document,
          ProtocolUserPdfCapabilityDocumentSync.updatedDocument(document, relativeDocumentPath));
    }
  }

  @Test
  void main_requiresOneRepositoryRootArgument() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ProtocolUserPdfCapabilityDocumentSyncMain.main(new String[0]));
  }
}
