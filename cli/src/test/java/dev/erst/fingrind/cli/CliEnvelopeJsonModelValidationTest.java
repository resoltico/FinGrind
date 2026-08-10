package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliDiscoveryCommonJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import dev.erst.fingrind.cli.json.CliPlanResultJsonModels;
import dev.erst.fingrind.cli.json.CliQueryPlanRejectionJsonModels;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationDisposition;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Validates the status-gated JSON response envelope. */
class CliEnvelopeJsonModelValidationTest {
  @Test
  void responseModels_trimTextAndRejectBlankValues() {
    CliEnvelopeJsonModels.Envelope<?> envelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.REJECTED,
            null,
            " query-book-not-initialized ",
            " The book is not initialized. ",
            " Repair hint. ",
            null,
            " idem-1 ",
            null,
            null,
            null,
            null,
            null);
    assertEquals(ProtocolEnvelopeStatus.REJECTED, envelope.status());
    assertEquals("query-book-not-initialized", envelope.code());
    assertEquals("The book is not initialized.", envelope.message());
    assertEquals("Repair hint.", envelope.hint());
    assertEquals("idem-1", envelope.idempotencyKey());
    assertThrows(
        NullPointerException.class,
        () ->
            new CliEnvelopeJsonModels.Envelope<>(
                nullOf(), null, "code", "message", null, null, null, null, null, null, null, null));
  }

  @Test
  void envelopeModels_enforceStatusSpecificDetailFamiliesAndForbiddenFields() {
    String planOperation = ProtocolCatalog.operationName(OperationId.EXECUTE_PLAN);
    CliEnvelopeJsonModels.Envelope<?> rejectedEnvelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.REJECTED,
            null,
            "unknown-account",
            "Unknown account.",
            "Declare the account and retry.",
            null,
            null,
            new CliQueryPlanRejectionJsonModels.UnknownAccountDetails("9999"),
            null,
            null,
            null,
            null);
    assertInstanceOf(
        CliQueryPlanRejectionJsonModels.UnknownAccountDetails.class, rejectedEnvelope.details());

    CliEnvelopeJsonModels.Envelope<?> errorEnvelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.ERROR,
            null,
            "invalid-request",
            "Request violates the schema.",
            "Fix the listed fields and retry.",
            "--request-file",
            null,
            new CliErrorJsonModels.InvalidRequestDetails(List.of("accountCode is required")),
            null,
            null,
            null,
            null);
    assertInstanceOf(CliErrorJsonModels.InvalidRequestDetails.class, errorEnvelope.details());

    IllegalArgumentException okForbiddenField =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.OK,
                    new CliDiscoveryCommonJsonModels.CommandCountPayload("query", 1),
                    "query-count",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));
    assertEquals("code must be absent for this envelope status.", okForbiddenField.getMessage());

    IllegalArgumentException rejectedMismatch =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.REJECTED,
                    null,
                    "unknown-account",
                    "Unknown account.",
                    null,
                    null,
                    null,
                    new CliErrorJsonModels.InvalidRequestDetails(
                        List.of("accountCode is required")),
                    null,
                    null,
                    null,
                    null));
    assertEquals("Rejected envelopes only admit rejection details.", rejectedMismatch.getMessage());

    IllegalArgumentException errorMismatch =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.ERROR,
                    null,
                    "invalid-request",
                    "Request violates the schema.",
                    null,
                    null,
                    null,
                    new CliQueryPlanRejectionJsonModels.UnknownAccountDetails("9999"),
                    null,
                    null,
                    null,
                    null));
    assertEquals("Error envelopes only admit error details.", errorMismatch.getMessage());

    IllegalArgumentException nonPlanPayloadMismatch =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.REJECTED,
                    new CliDiscoveryCommonJsonModels.CommandCountPayload("query", 1),
                    "unknown-account",
                    "Unknown account.",
                    null,
                    null,
                    null,
                    new CliQueryPlanRejectionJsonModels.UnknownAccountDetails("9999"),
                    null,
                    null,
                    null,
                    null));
    assertEquals(
        "payload must be absent unless this non-success envelope carries a "
            + planOperation
            + " result.",
        nonPlanPayloadMismatch.getMessage());

    IllegalArgumentException rejectedPlanStatusMismatch =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.REJECTED,
                    samplePlanPayload(LedgerPlanStatus.ASSERTION_FAILED),
                    "assertion-failed",
                    "Plan assertion failed.",
                    null,
                    null,
                    null,
                    new CliQueryPlanRejectionJsonModels.UnknownAccountDetails("9999"),
                    null,
                    null,
                    null,
                    null));
    assertEquals(
        "Rejected " + planOperation + " envelopes must carry a rejected plan payload.",
        rejectedPlanStatusMismatch.getMessage());

    IllegalArgumentException errorPlanStatusMismatch =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.ERROR,
                    samplePlanPayload(LedgerPlanStatus.REJECTED),
                    "plan-rejected",
                    "Plan rejected.",
                    null,
                    null,
                    null,
                    new CliErrorJsonModels.InvalidRequestDetails(List.of("request failed")),
                    null,
                    null,
                    null,
                    null));
    assertEquals(
        "Error " + planOperation + " envelopes must carry an assertion-failed plan payload.",
        errorPlanStatusMismatch.getMessage());

    CliEnvelopeJsonModels.Envelope<?> rejectedPlanEnvelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.REJECTED,
            samplePlanPayload(LedgerPlanStatus.REJECTED),
            "plan-rejected",
            "Plan rejected.",
            null,
            null,
            null,
            new CliQueryPlanRejectionJsonModels.UnknownAccountDetails("9999"),
            null,
            null,
            null,
            null);
    assertInstanceOf(
        CliPlanResultJsonModels.LedgerPlanPayload.class, rejectedPlanEnvelope.payload());

    CliEnvelopeJsonModels.Envelope<?> errorPlanEnvelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.ERROR,
            samplePlanPayload(LedgerPlanStatus.ASSERTION_FAILED),
            "assertion-failed",
            "Plan assertion failed.",
            null,
            null,
            null,
            new CliErrorJsonModels.InvalidRequestDetails(List.of("assertion failed")),
            null,
            null,
            null,
            null);
    assertInstanceOf(CliPlanResultJsonModels.LedgerPlanPayload.class, errorPlanEnvelope.payload());

    IllegalArgumentException emptyArtifacts =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.OK,
                    new CliDiscoveryCommonJsonModels.CommandCountPayload("query", 1),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null));
    assertEquals("artifacts must not be empty when present.", emptyArtifacts.getMessage());

    IllegalArgumentException duplicateArtifacts =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.OK,
                    new CliDiscoveryCommonJsonModels.CommandCountPayload("query", 1),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(
                        new CliEnvelopeJsonModels.SuccessArtifact(
                            "pdf",
                            "/tmp/report.pdf",
                            new CliEnvelopeJsonModels.PublicationTransaction(
                                "tx-first", "complete", "all-committed", "complete")),
                        new CliEnvelopeJsonModels.SuccessArtifact(
                            "pdf",
                            "/tmp/report.pdf",
                            new CliEnvelopeJsonModels.PublicationTransaction(
                                "tx-second", "complete", "all-committed", "complete"))),
                    null,
                    null,
                    null));
    assertEquals("artifacts must not contain duplicate entries.", duplicateArtifacts.getMessage());
  }

  @Test
  void successArtifact_preservesCanonicalPublicationAndTransactionCompletionEvidence() {
    Path publishedArtifactPath = Path.of("private-reports", "trial-balance.pdf");
    var publication =
        CliPublicationTransactionTestFixtures.completedArtifact(publishedArtifactPath);
    CliEnvelopeJsonModels.Envelope<?> envelope =
        CliEnvelopeMapper.successEnvelope(
            new CliDiscoveryCommonJsonModels.CommandCountPayload("query", 1), publication);

    CliEnvelopeJsonModels.SuccessArtifact artifact =
        Objects.requireNonNull(envelope.artifacts(), "artifacts").getFirst();
    assertEquals("pdf", artifact.format());
    assertEquals(CliPublicPaths.absoluteValue(publishedArtifactPath), artifact.path());
    assertNull(artifact.retainedStage());
    assertEquals(
        "0123456789abcdef0123456789abcdef",
        Objects.requireNonNull(artifact.publicationTransaction(), "publicationTransaction").id());
    assertTrue(CliWireJson.jsonText(envelope).contains("\"publicationTransaction\":"));

    String rendered = CliArtifactOutputRenderer.renderPdfArtifact(publication);
    assertEquals("Artifact", rendered.lines().findFirst().orElseThrow());
    assertTrue(rendered.contains("Publication transaction"));
    assertTrue(rendered.contains("0123456789abcdef0123456789abcdef"));
  }

  @Test
  void successArtifact_requiresExactlyOnePublicationEvidenceForm() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new CliEnvelopeJsonModels.SuccessArtifact("pdf", "/tmp/report.pdf", null, null));

    assertEquals(
        "A success artifact requires exactly one publication-evidence form.",
        exception.getMessage());
  }

  @Test
  void outcomeUncertainDetails_acceptNoStageOrOneRetainedStageFact() {
    CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails cleanOutcome =
        new CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails(
            "/tmp/candidate.pdf", null);
    CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails stagedOutcome =
        new CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails(
            "/tmp/candidate.pdf", "/tmp/candidate-stage.tmp");

    assertEquals("/tmp/candidate.pdf", cleanOutcome.candidateArtifact());
    assertNull(cleanOutcome.retainedStage());
    assertEquals("/tmp/candidate-stage.tmp", stagedOutcome.retainedStage());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails(
                "/tmp/candidate.pdf", "  "));
  }

  @Test
  void errorArtifactDetails_rejectAFinalOrCandidateThatClaimsToBeItsOwnRetainedStage() {
    IllegalArgumentException outcomeException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails(
                    "/tmp/candidate.pdf", "/tmp/candidate.pdf"));
    assertEquals(
        "candidateArtifact and retainedStage must identify distinct artifacts.",
        outcomeException.getMessage());

    IllegalArgumentException publishedException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliMaintenanceErrorJsonModels.PublishedArtifact(
                    "/tmp/published.pdf", "/tmp/published.pdf"));
    assertEquals(
        "path and retainedStage must identify distinct artifacts.",
        publishedException.getMessage());
  }

  private static CliPlanResultJsonModels.LedgerPlanPayload samplePlanPayload(
      LedgerPlanStatus status) {
    return new CliPlanResultJsonModels.LedgerPlanPayload(
        "plan-1",
        status,
        PlanResultDetail.SUMMARY,
        new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
            "2026-05-14T10:00:00Z",
            "2026-05-14T10:00:01Z",
            1,
            status == LedgerPlanStatus.SUCCEEDED ? 1 : 0,
            status == LedgerPlanStatus.SUCCEEDED ? 0 : 1,
            status == LedgerPlanStatus.SUCCEEDED ? null : "step-1"),
        status == LedgerPlanStatus.SUCCEEDED ? LedgerPlanAttestationDisposition.READ_ONLY : null,
        null,
        null);
  }
}
