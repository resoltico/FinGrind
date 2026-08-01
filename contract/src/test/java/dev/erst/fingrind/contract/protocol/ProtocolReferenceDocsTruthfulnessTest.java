package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.runtime.BookFormatContract;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
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

  @Test
  void attestationVerificationReferenceAtom_coversEveryPublishedFailureAndDiagnosticContract()
      throws IOException {
    String document =
        Files.readString(
                repositoryRoot()
                    .resolve("docs/DOC_02_VerifiableOperationAttestationVerification.md"))
            .replace("\r\n", "\n");

    for (AttestationVerificationFailure failure : AttestationVerificationFailure.values()) {
      assertTrue(
          document.contains("`" + failure.wireCode() + "`")
              || document.contains("| " + failure.wireCode() + " |"),
          () ->
              "docs/DOC_02_VerifiableOperationAttestationVerification.md must publish "
                  + failure.wireCode()
                  + ".");
    }
    assertTrue(document.contains("### Operator Diagnostics\n"));
    assertTrue(document.contains("context-neutral immutable-evidence description"));
    assertTrue(document.contains("One internal catalog derives every"));
    assertTrue(document.contains("public contextual projection from those facts"));
    assertTrue(document.contains("generic signer error"));
    assertTrue(
        document.contains("payload.fullContract.responseModel.attestationAdmissionDiagnostics[]"));
    assertTrue(
        document.contains(
            "payload.fullContract.responseModel.attestationVerificationDiagnostics[]"));
  }

  @Test
  void attestationReviewWindowReferenceAtoms_preserveTheExplicitOpenEndedBound()
      throws IOException {
    String verificationReference =
        Files.readString(
            repositoryRoot().resolve("docs/DOC_02_VerifiableOperationAttestationVerification.md"));
    String operatorReference =
        Files.readString(repositoryRoot().resolve("docs/USER_BOOK_ATTESTATION.md"));
    String responseReference = Files.readString(repositoryRoot().resolve("docs/USER_RESPONSES.md"));

    assertTrue(
        verificationReference.contains(
            "its details always retain `lastAffectedOrder`, using JSON `null`"));
    assertTrue(
        operatorReference.contains(
            "Both that error and a finding return\n"
                + "the declaration's `lastAffectedOrder` field explicitly"));
    assertTrue(operatorReference.contains("JSON uses `null` for an open-ended\ninterval"));
    assertTrue(responseReference.contains("always-present nullable `details.lastAffectedOrder`"));
    assertTrue(responseReference.contains("`lastAffectedOrder: null`"));
  }

  @Test
  void responseDiscoveryReferenceAtoms_routeThePublishedAttestationDiagnosticDescriptors()
      throws IOException {
    String responseIndex =
        Files.readString(repositoryRoot().resolve("docs/DOC_00_ResponseAndWorkflow.md"));
    String machineContract =
        Files.readString(repositoryRoot().resolve("docs/DOC_02_MachineContractAndDescriptors.md"));
    String operatorResponses = Files.readString(repositoryRoot().resolve("docs/USER_RESPONSES.md"));

    for (String symbol :
        List.of(
            "AttestationDiagnosticDescriptors",
            "AttestationDiagnosticDescriptors.AdmissionContext",
            "AttestationDiagnosticDescriptors.DiagnosticDescriptor",
            "AttestationDiagnosticDescriptors.AdmissionDiagnosticsDescriptor",
            "AttestationDiagnosticDescriptors.VerificationDiagnosticsDescriptor")) {
      assertTrue(
          responseIndex.contains(symbol),
          () -> "docs/DOC_00_ResponseAndWorkflow.md must route " + symbol + ".");
      assertTrue(
          machineContract.contains(symbol),
          () -> "docs/DOC_02_MachineContractAndDescriptors.md must describe " + symbol + ".");
    }
    assertTrue(
        operatorResponses.contains(
            "payload.fullContract.responseModel.attestationAdmissionDiagnostics"));
    assertTrue(
        operatorResponses.contains(
            "payload.fullContract.responseModel.attestationVerificationDiagnostics"));
  }

  @Test
  void attestationReceiptReferenceAtom_namesTheExactSourceVerificationOutcome() throws IOException {
    String section =
        markdownSection(
            "docs/DOC_02_VerifiableOperationAttestationArtifacts.md",
            "## `Receipt Result Types`\n");

    assertTrue(section.contains("three closed outcomes"));
    assertTrue(section.contains("`VerificationRejected`"));
    assertTrue(
        section.contains("before credential loading, receipt staging, or publication begins"));
  }

  @Test
  void attestationReceiptReferenceAtom_publishesTheCompleteVerifiedAnchorTuple()
      throws IOException {
    String section =
        markdownSection(
            "docs/DOC_02_VerifiableOperationAttestationArtifacts.md",
            "## `Receipt Result Types`\n");

    assertTrue(
        section.contains(
            "`VerifyAttestationReceiptResult.Valid` publishes the complete verified anchor tuple"));
    assertTrue(section.contains("`bookId`"));
    assertTrue(section.contains("`operationOrder`"));
    assertTrue(section.contains("`operationHead`"));
  }

  @Test
  void userReceiptGuides_publishTheAnchorForBothSuccessfulReceiptCommands() throws IOException {
    String responses =
        Files.readString(repositoryRoot().resolve("docs/USER_RESPONSES.md"))
            .replaceAll("\\s+", " ");
    String attestationGuide =
        Files.readString(repositoryRoot().resolve("docs/USER_BOOK_ATTESTATION.md"))
            .replaceAll("\\s+", " ");

    assertTrue(
        responses.contains(
            "`export-attestation-receipt` and `verify-receipt` success each publish the complete"
                + " receipt anchor"),
        "USER_RESPONSES.md must publish receiptAttestationAnchor for export and verification.");
    assertTrue(
        responses.contains("`payload.receiptAttestationAnchor.{operationOrder,operationHead}`"));
    assertTrue(
        attestationGuide.contains(
            "Both successful receipt surfaces publish the complete receipt anchor"),
        "USER_BOOK_ATTESTATION.md must publish the shared successful receipt anchor.");
    assertTrue(attestationGuide.contains("`receiptAttestationAnchor` object"));
  }

  @Test
  void backupBookResultReferenceAtom_coversThePublishedAcknowledgementLifecycle()
      throws IOException {
    String section =
        markdownSection("docs/DOC_02_AdministrationAndReports.md", "## `BackupBookResult`\n")
            .replaceAll("\\s+", " ");

    assertTrue(
        section.contains(
            "`BackedUp`, `AcknowledgementPending`, `AcknowledgementAuthorizationRejected`, and "
                + "`Rejected`"),
        "docs/DOC_02_AdministrationAndReports.md must name every BackupBookResult variant.");
    assertTrue(
        section.contains("`acknowledged` always carries the exact append commit"),
        "The reference atom must preserve BackedUp's acknowledgement/commit invariant.");
    assertTrue(
        section.contains("rerun the exact tuple"),
        "The reference atom must preserve exact-tuple acknowledgement recovery.");
    assertTrue(
        section.contains("current-head authorization refused its source-book acknowledgement"),
        "The reference atom must distinguish authorization refusal from operational pending state.");
  }

  @Test
  void attestationVerificationReferenceAtom_requiresRegistryToMatchTheWholeVerifiedHead()
      throws IOException {
    String section =
        markdownSection(
            "docs/DOC_02_VerifiableOperationAttestationVerification.md",
            "## `AttestationBookInspection` And `AttestationRegistryInspection`\n");

    assertTrue(section.contains("book ID, head order, and operation head exactly match"));
  }

  @Test
  void userDiscoveryGuides_publishTheCurrentMachineProtocolVersion() throws IOException {
    String expected = "current hard-break line is `\"" + MachineContract.protocolVersion() + "\"`";
    for (String relativePath : List.of("docs/USER_CLI.md", "docs/USER_RESPONSES.md")) {
      String document =
          Files.readString(repositoryRoot().resolve(relativePath)).replace("\r\n", "\n");
      assertTrue(
          document.contains(expected),
          () -> relativePath + " must publish MachineContract.protocolVersion().");
    }
  }

  @Test
  void currentAttestationReferenceDocs_publishCurrentLinesAndRejectImmediatelyRetiredLines()
      throws IOException {
    int currentProtocolVersion = Integer.parseInt(MachineContract.protocolVersion());
    int retiredProtocolVersion = currentProtocolVersion - 1;
    int currentFormatVersion = BookFormatContract.FORMAT_VERSION;
    int retiredFormatVersion = BookFormatContract.FORMAT_VERSION - 1;
    List<String> currentProtocolMentions = versionMentions("protocol", currentProtocolVersion);
    List<String> retiredProtocolMentions = versionMentions("protocol", retiredProtocolVersion);
    List<String> currentFormatMentions = versionMentions("format", currentFormatVersion);
    List<String> retiredFormatMentions = versionMentions("format", retiredFormatVersion);
    for (String relativePath :
        List.of(
            "docs/DOC_02_VerifiableOperationAttestation.md",
            "docs/DOC_02_VerifiableOperationAttestationProfiles.md",
            "docs/DOC_02_VerifiableOperationAttestationCorpus.md",
            "docs/DOC_02_VerifiableOperationAttestationVerification.md",
            "docs/DOC_02_VerifiableOperationAttestationArtifacts.md",
            "docs/USER_BOOK_ATTESTATION.md")) {
      String document =
          Files.readString(repositoryRoot().resolve(relativePath)).replace("\r\n", "\n");
      assertTrue(
          containsAny(document, currentProtocolMentions),
          () -> relativePath + " must publish MachineContract.protocolVersion().");
      assertFalse(
          containsAny(document, retiredProtocolMentions),
          () -> relativePath + " must not retain the immediately retired protocol identity.");
      assertTrue(
          containsAny(document, currentFormatMentions),
          () -> relativePath + " must publish BookFormatContract.FORMAT_VERSION.");
      assertFalse(
          containsAny(document, retiredFormatMentions),
          () -> relativePath + " must not retain the immediately retired book-format identity.");
    }
  }

  @Test
  void machineContractReference_documentsCanonicalCommandDisplayLabels() throws IOException {
    String document =
        Files.readString(repositoryRoot().resolve("docs/DOC_02_MachineContractAndDescriptors.md"))
            .replace("\r\n", "\n");

    assertTrue(
        document.contains(
            "`CommandDescriptor.displayLabel` is required to equal the corresponding "
                + "`ProtocolCatalog`"),
        "Machine-contract reference must document the canonical command display-label invariant.");
    assertTrue(
        document.contains("Compact command surfaces project that same field."),
        "Machine-contract reference must document compact capability label projection.");
  }

  @Test
  void sqliteDeveloperReference_publishesTheCurrentProtectedBookFormat() throws IOException {
    String document =
        Files.readString(repositoryRoot().resolve("docs/DEVELOPER_SQLITE.md"))
            .replace("\r\n", "\n");
    String expected =
        "the current supported book format is `"
            + BookFormatContract.FORMAT_VERSION
            + "`, owned by `BookFormatContract`";

    assertTrue(
        document.contains(expected),
        "docs/DEVELOPER_SQLITE.md must publish BookFormatContract.FORMAT_VERSION.");
  }

  @Test
  void sqliteDeveloperReference_routesRuntimeFactsThroughTheEnvironmentCommand()
      throws IOException {
    String section =
        markdownSection("docs/DEVELOPER_SQLITE.md", "## Runtime Behavior\n")
            .replaceAll("\\s+", " ");

    assertTrue(
        section.contains("through the `environment` command"),
        "docs/DEVELOPER_SQLITE.md must route runtime facts through the environment command.");
    assertFalse(
        section.contains("plaintextHeaderSize` through `capabilities`"),
        "docs/DEVELOPER_SQLITE.md must not claim capabilities publishes environment facts.");
  }

  private String accountingEvidenceSection() throws IOException {
    return markdownSection("docs/DOC_01_Core.md", "## `AccountingEvidence`\n");
  }

  private static List<String> versionMentions(String noun, int version) {
    String value = Integer.toString(version);
    String capitalizedNoun = Character.toUpperCase(noun.charAt(0)) + noun.substring(1);
    return List.of(
        noun + " " + value,
        noun + "-" + value,
        capitalizedNoun + " " + value,
        capitalizedNoun + "-" + value);
  }

  private static boolean containsAny(String document, List<String> mentions) {
    return mentions.stream().anyMatch(document::contains);
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
