package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import dev.erst.fingrind.cli.json.CliOpenBookErrorJsonModels;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Validates the invariant-bearing JSON forms for retained protected-book opening failures. */
class CliOpenBookErrorJsonModelValidationTest extends CliBookWorkflowFixtureSupport {
  private static final String BOOK_FILE = "/tmp/fingrind-open-book-validation/book.fgr";
  private static final String ATTESTATION_BOOK_ID = "book-1";
  private static final String OPERATION_HEAD = "a".repeat(64);
  private static final String CREDENTIAL_KEY_ID = "b".repeat(64);

  @Test
  void preparationRetentionDetails_requireUniqueNonemptyArtifactFacts() {
    CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact founderArtifact =
        retainedArtifact(
            "attestation-founder-key", "/tmp/fingrind-open-book-validation/founder.fgatk");
    CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact bookArtifact =
        retainedArtifact("book-file", BOOK_FILE);
    List<CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact> mutableArtifacts =
        new ArrayList<>(List.of(founderArtifact, bookArtifact));

    CliOpenBookErrorJsonModels.OpenBookPreparationArtifactsRetainedDetails details =
        new CliOpenBookErrorJsonModels.OpenBookPreparationArtifactsRetainedDetails(
            mutableArtifacts);
    mutableArtifacts.clear();

    assertEquals(List.of(founderArtifact, bookArtifact), details.retainedArtifacts());
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.InvalidRequestDetails(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliOpenBookErrorJsonModels.OpenBookPreparationArtifactsRetainedDetails(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliOpenBookErrorJsonModels.OpenBookPreparationArtifactsRetainedDetails(
                List.of(
                    founderArtifact, retainedArtifact("book-sidecar", founderArtifact.path()))));
  }

  @Test
  void completionUncertaintyDetails_requireOneCoherentTrustRootAndArtifactInventory() {
    CliAttestationJsonModels.AttestationCommitPayload attestationCommit =
        new CliAttestationJsonModels.AttestationCommitPayload("0", OPERATION_HEAD);
    CliOpenBookErrorJsonModels.ReportedAttestationTrustRoot trustRoot =
        trustRoot(ATTESTATION_BOOK_ID, attestationCommit);
    CliEnvelopeJsonModels.SuccessArtifact founderKey =
        new CliEnvelopeJsonModels.SuccessArtifact(
            "attestation-key",
            "/tmp/fingrind-open-book-validation/founder.fgatk",
            "/tmp/fingrind-open-book-validation/.founder.fgatk-stage");
    CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact bookArtifact =
        retainedArtifact("book-file", BOOK_FILE);
    CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact sidecarArtifact =
        retainedArtifact("book-sidecar", BOOK_FILE + "-wal");
    List<CliEnvelopeJsonModels.SuccessArtifact> mutableFounderKeys =
        new ArrayList<>(List.of(founderKey));
    List<CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact> mutableRetainedArtifacts =
        new ArrayList<>(List.of(bookArtifact, sidecarArtifact));

    CliOpenBookErrorJsonModels.OpenBookCompletionUncertainDetails details =
        completionDetails(
            ATTESTATION_BOOK_ID,
            attestationCommit,
            trustRoot,
            mutableFounderKeys,
            mutableRetainedArtifacts);
    mutableFounderKeys.clear();
    mutableRetainedArtifacts.clear();

    assertEquals(BOOK_FILE, details.bookFile());
    assertEquals(List.of(founderKey), details.retainedFounderKeyArtifacts());
    assertEquals(List.of(bookArtifact, sidecarArtifact), details.retainedBookArtifacts());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                "different-book",
                attestationCommit,
                trustRoot,
                List.of(founderKey),
                List.of(bookArtifact)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                ATTESTATION_BOOK_ID,
                new CliAttestationJsonModels.AttestationCommitPayload("1", OPERATION_HEAD),
                trustRoot,
                List.of(founderKey),
                List.of(bookArtifact)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                ATTESTATION_BOOK_ID, attestationCommit, trustRoot, List.of(founderKey), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                ATTESTATION_BOOK_ID,
                attestationCommit,
                trustRoot,
                List.of(founderKey),
                List.of(bookArtifact, retainedArtifact("book-sidecar", BOOK_FILE))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                ATTESTATION_BOOK_ID,
                attestationCommit,
                trustRoot,
                List.of(
                    founderKey,
                    new CliEnvelopeJsonModels.SuccessArtifact(
                        "attestation-key-copy",
                        founderKey.path(),
                        "/tmp/fingrind-open-book-validation/.founder-copy.fgatk-stage")),
                List.of(bookArtifact)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                ATTESTATION_BOOK_ID,
                attestationCommit,
                trustRoot,
                List.of(founderKey),
                List.of(sidecarArtifact)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                ATTESTATION_BOOK_ID,
                attestationCommit,
                trustRoot,
                List.of(founderKey),
                List.of(retainedArtifact("book-file", BOOK_FILE + "-different"))));
  }

  @Test
  void reviewWindowDetails_renderOpenAndBoundedIntervalsAndRejectIntervalsInsideTheHead() {
    CliErrorJsonModels.AttestationReviewWindowDetails openInterval =
        new CliErrorJsonModels.AttestationReviewWindowDetails(CREDENTIAL_KEY_ID, "8", null, "7");
    CliErrorJsonModels.AttestationReviewWindowDetails boundedInterval =
        new CliErrorJsonModels.AttestationReviewWindowDetails(CREDENTIAL_KEY_ID, "8", "9", "7");

    assertNull(openInterval.lastAffectedOrder());
    assertEquals("9", boundedInterval.lastAffectedOrder());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliErrorJsonModels.AttestationReviewWindowDetails(
                CREDENTIAL_KEY_ID, "8", "7", "7"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliErrorJsonModels.AttestationReviewWindowDetails(
                CREDENTIAL_KEY_ID, "6", null, "7"));

    String openText = renderReviewWindow(openInterval);
    String boundedText = renderReviewWindow(boundedInterval);
    assertTrue(openText.contains("(through verified head)"), openText);
    assertTrue(boundedText.contains("Last affected order"), boundedText);
    assertTrue(boundedText.contains("9"), boundedText);

    String cleanOutcomeText =
        CliFailureOutputRenderer.renderFailureText(
            new CliFailure(
                ContractErrors.Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN.code(),
                "Artifact publication outcome is unconfirmed.",
                null,
                "--pdf-out",
                new CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails(
                    "/tmp/fingrind-open-book-validation/candidate.pdf", null)));
    assertTrue(cleanOutcomeText.contains("Candidate artifact path"), cleanOutcomeText);
    assertFalse(cleanOutcomeText.contains("Retained stage path"), cleanOutcomeText);
  }

  private static CliOpenBookErrorJsonModels.OpenBookCompletionUncertainDetails completionDetails(
      String attestationBookId,
      CliAttestationJsonModels.AttestationCommitPayload attestationCommit,
      CliOpenBookErrorJsonModels.ReportedAttestationTrustRoot trustRoot,
      List<CliEnvelopeJsonModels.SuccessArtifact> founderKeys,
      List<CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact> retainedArtifacts) {
    return new CliOpenBookErrorJsonModels.OpenBookCompletionUncertainDetails(
        BOOK_FILE,
        "2026-07-26T12:00:00Z",
        CliBookInspectionPayloadMapper.bookIdentityPayload(bookIdentity()),
        attestationBookId,
        attestationCommit,
        trustRoot,
        founderKeys,
        retainedArtifacts);
  }

  private static CliOpenBookErrorJsonModels.ReportedAttestationTrustRoot trustRoot(
      String bookId, CliAttestationJsonModels.AttestationCommitPayload attestationCommit) {
    return new CliOpenBookErrorJsonModels.ReportedAttestationTrustRoot(
        bookId,
        attestationCommit,
        new CliAttestationJsonModels.AttestationRegistryPayload(
            List.of(), List.of(), List.of(), List.of()));
  }

  private static CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact retainedArtifact(
      String role, String path) {
    return new CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact(role, path, null);
  }

  private static String renderReviewWindow(
      CliErrorJsonModels.AttestationReviewWindowDetails details) {
    return CliFailureOutputRenderer.renderFailureText(
        new CliFailure(
            ContractErrors.Descriptor.ATTESTATION_REVIEW_WINDOW_EXCEEDS_HEAD.code(),
            "The review window exceeds the verified attestation head.",
            null,
            null,
            details));
  }
}
