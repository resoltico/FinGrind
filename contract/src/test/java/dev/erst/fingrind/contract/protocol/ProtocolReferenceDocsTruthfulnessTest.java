package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** Truthfulness checks for public AFAD reference atoms that describe core contract owners. */
class ProtocolReferenceDocsTruthfulnessTest extends ProtocolContractRepositorySupport {
  @Test
  void accountingEvidenceReferenceAtom_matchesCurrentValidationAndDurableBoundary()
      throws IOException {
    String section = accountingEvidenceSection();

    assertTrue(
        section.contains(
            "- Validation: rejects `null` collections and rejects empty `sourceDocuments`"),
        "docs/DOC_01_Core.md must describe the live AccountingEvidence validation boundary.");
    assertTrue(
        section.contains(
            "- Durable boundary: duplicate source-document ids or approval ids remain representable in memory"),
        "docs/DOC_01_Core.md must describe where duplicate evidence identifiers are actually rejected.");
    assertTrue(
        section.contains("protected-book posting uniqueness constraints"),
        "docs/DOC_01_Core.md must name the durable uniqueness boundary that rejects duplicate evidence identifiers.");
    assertFalse(
        section.contains("rejects duplicate"),
        "docs/DOC_01_Core.md must not claim duplicate evidence identifiers are rejected in-memory.");
  }

  @Test
  void reportingPeriodReferenceAtom_namesCashReceiptsAndPaymentsAsCurrentBoundedConsumer()
      throws IOException {
    String section = markdownSection("docs/DOC_01_Core.md", "## `ReportingPeriod`\n");

    assertTrue(
        section.contains("statements of cash receipts and payments"),
        "docs/DOC_01_Core.md must name the cash receipts and payments statement as a current ReportingPeriod consumer.");
  }

  @Test
  void userRequestsReportSummary_includesCashFlowComparativePayloadFamilies() throws IOException {
    String document =
        Files.readString(repositoryRoot().resolve("docs/USER_REQUESTS.md"))
            .replace("\r\n", "\n")
            .replaceAll("\\s+", " ");

    assertTrue(
        document.contains("`cash-flow-statement` carries `comparativeOpeningCashTotals[]`"),
        "docs/USER_REQUESTS.md must describe the comparative opening cash totals for cash-flow-statement.");
    assertTrue(
        document.contains("`comparativeMovementTotals[]`"),
        "docs/USER_REQUESTS.md must describe the comparative movement totals for cash-flow-statement.");
    assertTrue(
        document.contains("`comparativeClosingCashTotals[]`"),
        "docs/USER_REQUESTS.md must describe the comparative closing cash totals for cash-flow-statement.");
  }

  private String accountingEvidenceSection() throws IOException {
    return markdownSection("docs/DOC_01_Core.md", "## `AccountingEvidence`\n");
  }

  private String markdownSection(String relativePath, String heading) throws IOException {
    String document =
        Files.readString(repositoryRoot().resolve(relativePath)).replace("\r\n", "\n");
    int headingStart = document.indexOf(heading);
    assertTrue(headingStart >= 0, relativePath + " must contain the heading " + heading.strip());
    int nextHeadingStart = document.indexOf("\n## ", headingStart + heading.length());
    return nextHeadingStart >= 0
        ? document.substring(headingStart, nextHeadingStart)
        : document.substring(headingStart);
  }
}
