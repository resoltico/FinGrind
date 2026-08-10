package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import dev.erst.fingrind.core.attestation.AttestationReviewFinding;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Verifies attestation response envelopes, text presentation, and output-mode boundaries. */
class CliAttestationResponsePresentationTest extends CliAttestationTransportFixtures {
  @Test
  void attestationVerificationPayload_keepsReviewStateDerivableAndCanonical() {
    CliAttestationJsonModels.VerifyBookPayload noReview =
        new CliAttestationJsonModels.VerifyBookPayload(
            BOOK_ID.toString(),
            verifiedHead(BigInteger.ZERO),
            PREVIOUS_HEAD,
            false,
            List.of(),
            registryPayload());
    assertFalse(noReview.reviewRequired());
    assertEquals(PREVIOUS_HEAD, noReview.previousHead());
    assertEquals(verifiedHead(BigInteger.ZERO), noReview.verifiedAttestationHead());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.VerifyBookPayload(
                BOOK_ID.toString(),
                verifiedHead(BigInteger.ZERO),
                PREVIOUS_HEAD,
                true,
                List.of(),
                registryPayload()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.VerifyBookPayload(
                BOOK_ID.toString(),
                verifiedHead(BigInteger.ZERO),
                PREVIOUS_HEAD.toUpperCase(java.util.Locale.ROOT),
                false,
                List.of(),
                registryPayload()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.VerifyBookPayload(
                BOOK_ID.toString(),
                verifiedHead(BigInteger.ZERO),
                PREVIOUS_HEAD,
                false,
                List.of(reviewFindingPayload()),
                registryPayload()));
  }

  @Test
  void successEnvelopes_omitEmptyArtifactsAndPreservePublishedArtifacts() {
    CliAttestationJsonModels.VerifyBookPayload payload =
        new CliAttestationJsonModels.VerifyBookPayload(
            BOOK_ID.toString(),
            verifiedHead(BigInteger.ZERO),
            PREVIOUS_HEAD,
            false,
            List.of(),
            registryPayload());
    assertEquals(null, CliEnvelopeMapper.successEnvelope(payload, List.of()).artifacts());
    var artifacts =
        CliEnvelopeMapper.successEnvelope(
                payload,
                List.of(
                    CliEnvelopeMapper.successArtifact(
                        "pdf",
                        CliPublicationTransactionTestFixtures.completedArtifact(
                            Path.of("reports", "book.pdf")))))
            .artifacts();
    assertEquals(1, Objects.requireNonNull(artifacts).size());
  }

  @Test
  void receiptExportRows_includePublicationTransactionEvidence() {
    ExportAttestationReceiptResult.Exported exported =
        new ExportAttestationReceiptResult.Exported(
            CliPublicationTransactionTestFixtures.completedArtifact(
                Path.of("receipts", "retained.fgr")),
            BOOK_ID,
            BigInteger.TWO,
            OPERATION_HEAD,
            List.of());

    List<List<String>> rows = CliAttestationReadPresentation.receiptExportRows(exported);

    assertEquals(
        List.of("Publication transaction", "0123456789abcdef0123456789abcdef"), rows.getLast());
  }

  @Test
  void responseWriter_coversAttestationTextJsonAndUnsupportedCsvWithoutChangingResults() {
    CliBookReadResponseWriter writer =
        new CliBookReadResponseWriter(outputChannel(new ByteArrayOutputStream()), fixedClock());
    VerifyBookAttestationResult.Valid verification =
        new VerifyBookAttestationResult.Valid(
            BOOK_ID,
            BigInteger.TWO,
            OPERATION_HEAD,
            PREVIOUS_HEAD,
            List.of(reviewFinding()),
            registry(BigInteger.TWO));
    AttestationReviewResult review = reviewResult(BigInteger.TWO, List.of(reviewFinding()));
    ExportAttestationReceiptResult.Exported exported =
        new ExportAttestationReceiptResult.Exported(
            CliPublicationTransactionTestFixtures.completedArtifact(
                Path.of("receipts", "current.fgr")),
            BOOK_ID,
            BigInteger.TWO,
            OPERATION_HEAD,
            List.of("store independently"));
    VerifyAttestationReceiptResult.Valid receipt =
        new VerifyAttestationReceiptResult.Valid(
            Path.of("receipts", "current.fgr"),
            BOOK_ID,
            BigInteger.TWO,
            OPERATION_HEAD,
            List.of("receipt matches"));

    ByteArrayOutputStream verificationText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(verificationText), fixedClock())
        .writeVerifyBookAttestation(verification, false, OutputMode.TEXT);
    assertTrue(
        verificationText
            .toString(StandardCharsets.UTF_8)
            .contains("Book Attestation Valid — Review Required"));

    ByteArrayOutputStream strictVerificationJson = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(strictVerificationJson), fixedClock())
        .writeVerifyBookAttestation(verification, true, OutputMode.JSON);
    assertJsonContains(strictVerificationJson, "\"status\":\"rejected\"");
    assertJsonContains(strictVerificationJson, "\"code\":\"attestation-review-required\"");
    assertFalse(strictVerificationJson.toString(StandardCharsets.UTF_8).contains("\"payload\":"));
    assertJsonContains(strictVerificationJson, "\"details\"");
    assertJsonContains(strictVerificationJson, "\"bookId\":\"" + BOOK_ID + "\"");
    assertJsonContains(strictVerificationJson, "\"verifiedAttestationHead\"");
    assertJsonContains(strictVerificationJson, "\"operationOrder\":\"2\"");
    assertJsonContains(strictVerificationJson, "\"operationHead\":\"" + OPERATION_HEAD + "\"");
    assertJsonContains(strictVerificationJson, "\"previousHead\":\"" + PREVIOUS_HEAD + "\"");
    assertJsonContains(strictVerificationJson, "\"reviewFindings\"");

    ByteArrayOutputStream strictVerificationText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(strictVerificationText), fixedClock())
        .writeVerifyBookAttestation(verification, true, OutputMode.TEXT);
    String reviewRequiredText = strictVerificationText.toString(StandardCharsets.UTF_8);
    assertTrue(reviewRequiredText.contains("Book Attestation Review Required"));
    assertTrue(reviewRequiredText.contains("Book ID"));
    assertTrue(reviewRequiredText.contains("Attestation order"));
    assertTrue(reviewRequiredText.contains("Attestation head"));
    assertTrue(reviewRequiredText.contains("Previous attestation head"));
    assertFalse(reviewRequiredText.contains("Head order"));
    assertFalse(reviewRequiredText.contains("Operation head"));
    assertFalse(reviewRequiredText.contains("Previous head"));
    assertTrue(reviewRequiredText.contains("Review findings"));
    assertTrue(reviewRequiredText.contains("Review declaration"));
    assertTrue(reviewRequiredText.contains("Credential key ID:"));

    ByteArrayOutputStream reviewText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(reviewText), fixedClock())
        .writeAttestationReview(review, OutputMode.TEXT);
    assertTrue(reviewText.toString(StandardCharsets.UTF_8).contains("Credential key ID:"));

    ByteArrayOutputStream exportedJson = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(exportedJson), fixedClock())
        .writeExportAttestationReceipt(exported, OutputMode.JSON);
    assertJsonContains(exportedJson, "\"receiptFile\"");
    assertJsonContains(exportedJson, "\"store independently\"");
    var exportedPayload = new ObjectMapper().readTree(exportedJson.toByteArray()).path("payload");
    assertEquals(
        "2", exportedPayload.path("receiptAttestationAnchor").path("operationOrder").stringValue());
    assertEquals(
        OPERATION_HEAD,
        exportedPayload.path("receiptAttestationAnchor").path("operationHead").stringValue());
    assertFalse(exportedPayload.has("operationOrder"));
    assertFalse(exportedPayload.has("operationHead"));
    var exportedArtifacts =
        new ObjectMapper().readTree(exportedJson.toByteArray()).path("artifacts");
    assertEquals(1, exportedArtifacts.size());
    assertEquals(
        ProtocolArtifactOutput.attestationReceiptFormat(),
        exportedArtifacts.get(0).path("format").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(exported.receiptFilePath()),
        exportedArtifacts.get(0).path("path").stringValue());
    assertTrue(exportedArtifacts.get(0).path("retainedStage").isMissingNode());
    assertEquals(
        "0123456789abcdef0123456789abcdef",
        exportedArtifacts.get(0).path("publicationTransaction").path("id").stringValue());

    ByteArrayOutputStream exportedText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(exportedText), fixedClock())
        .writeExportAttestationReceipt(exported, OutputMode.TEXT);
    assertTrue(
        exportedText.toString(StandardCharsets.UTF_8).contains("Attestation Receipt Exported"));
    assertTrue(exportedText.toString(StandardCharsets.UTF_8).contains("Publication transaction"));
    assertTrue(
        exportedText.toString(StandardCharsets.UTF_8).contains("0123456789abcdef0123456789abcdef"));

    ByteArrayOutputStream authorizationRejectedJson = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(authorizationRejectedJson), fixedClock())
        .writeExportAttestationReceipt(
            new ExportAttestationReceiptResult.AuthorizationRejected(
                AttestationVerificationFailure.QUORUM_BELOW),
            OutputMode.JSON);
    assertJsonContains(authorizationRejectedJson, "\"status\":\"rejected\"");
    assertJsonContains(authorizationRejectedJson, "\"code\":\"attestation-quorum-below\"");

    ByteArrayOutputStream receiptText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(receiptText), fixedClock())
        .writeVerifyAttestationReceipt(receipt, OutputMode.TEXT);
    assertTrue(receiptText.toString(StandardCharsets.UTF_8).contains("Attestation Receipt Valid"));
    assertTextContains(receiptText, "Attestation order", "2");
    assertTextContains(receiptText, "Attestation head", OPERATION_HEAD);

    ByteArrayOutputStream emptyFindingsText = new ByteArrayOutputStream();
    CliBookReadResponseWriter emptyFindingsWriter =
        new CliBookReadResponseWriter(outputChannel(emptyFindingsText), fixedClock());
    emptyFindingsWriter.writeAttestationReview(
        reviewResult(BigInteger.ZERO, List.of()), OutputMode.TEXT);
    emptyFindingsWriter.writeExportAttestationReceipt(
        new ExportAttestationReceiptResult.Exported(
            CliPublicationTransactionTestFixtures.completedArtifact(
                Path.of("receipts", "empty.fgr")),
            BOOK_ID,
            BigInteger.ZERO,
            OPERATION_HEAD,
            List.of()),
        OutputMode.TEXT);
    emptyFindingsWriter.writeVerifyAttestationReceipt(
        new VerifyAttestationReceiptResult.Valid(
            Path.of("receipts", "empty.fgr"), BOOK_ID, BigInteger.ZERO, OPERATION_HEAD, List.of()),
        OutputMode.TEXT);
    assertTrue(emptyFindingsText.toString(StandardCharsets.UTF_8).contains("(none)"));

    AttestationReviewFinding boundedFinding =
        new AttestationReviewFinding(
            new AttestationCompromiseReview("b".repeat(64), BigInteger.ONE, BigInteger.TWO),
            BigInteger.TWO);
    ByteArrayOutputStream boundedJson = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(boundedJson), fixedClock())
        .writeVerifyBookAttestation(
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                BigInteger.TWO,
                OPERATION_HEAD,
                PREVIOUS_HEAD,
                List.of(boundedFinding),
                registry(BigInteger.TWO)),
            false,
            OutputMode.JSON);
    assertJsonContains(boundedJson, "\"lastAffectedOrder\":\"2\"");

    AttestationReviewFinding openEndedFinding =
        new AttestationReviewFinding(
            new AttestationCompromiseReview("c".repeat(64), BigInteger.ONE, null), BigInteger.TWO);
    ByteArrayOutputStream openEndedJson = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(openEndedJson), fixedClock())
        .writeAttestationReview(
            reviewResult(BigInteger.TWO, List.of(openEndedFinding)), OutputMode.JSON);
    assertJsonContains(openEndedJson, "\"lastAffectedOrder\":null");

    ByteArrayOutputStream boundedText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(boundedText), fixedClock())
        .writeAttestationReview(
            reviewResult(BigInteger.TWO, List.of(boundedFinding)), OutputMode.TEXT);
    assertTrue(boundedText.toString(StandardCharsets.UTF_8).contains("Review window: 1 through 2"));
    assertTrue(
        boundedText.toString(StandardCharsets.UTF_8).contains("Affected operation orders: 2"));

    assertThrows(
        IllegalArgumentException.class,
        () -> writer.writeVerifyBookAttestation(verification, false, OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () -> writer.writeVerifyBookAttestation(verification, true, OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () -> writer.writeAttestationReview(review, OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () -> writer.writeExportAttestationReceipt(exported, OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () -> writer.writeVerifyAttestationReceipt(receipt, OutputMode.CSV));
  }

  @Test
  void attestationReviewText_groupsEachCompleteDeclarationAndCompressesOnlyConsecutiveOrders() {
    String credentialKeyId = "d".repeat(64);
    AttestationCompromiseReview firstDeclaration =
        new AttestationCompromiseReview(credentialKeyId, BigInteger.ONE, BigInteger.valueOf(9));
    AttestationCompromiseReview secondDeclaration =
        new AttestationCompromiseReview(credentialKeyId, BigInteger.TEN, BigInteger.valueOf(20));
    List<AttestationReviewFinding> findings =
        List.of(
            new AttestationReviewFinding(secondDeclaration, BigInteger.valueOf(11)),
            new AttestationReviewFinding(firstDeclaration, BigInteger.valueOf(8)),
            new AttestationReviewFinding(firstDeclaration, BigInteger.ONE),
            new AttestationReviewFinding(secondDeclaration, BigInteger.TEN),
            new AttestationReviewFinding(firstDeclaration, BigInteger.TWO),
            new AttestationReviewFinding(firstDeclaration, BigInteger.valueOf(5)),
            new AttestationReviewFinding(firstDeclaration, BigInteger.valueOf(3)),
            new AttestationReviewFinding(secondDeclaration, BigInteger.valueOf(13)),
            new AttestationReviewFinding(firstDeclaration, BigInteger.valueOf(7)));
    AttestationReviewResult review = reviewResult(BigInteger.valueOf(20), findings);
    VerifyBookAttestationResult.Valid verification =
        new VerifyBookAttestationResult.Valid(
            BOOK_ID,
            BigInteger.valueOf(20),
            OPERATION_HEAD,
            PREVIOUS_HEAD,
            findings,
            registry(BigInteger.valueOf(20)));

    ByteArrayOutputStream reviewOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(reviewOutput), fixedClock())
        .writeAttestationReview(review, OutputMode.TEXT);
    String reviewText = reviewOutput.toString(StandardCharsets.UTF_8);

    assertEquals(2, occurrences(reviewText, "Credential key ID: " + credentialKeyId));
    assertEquals(1, occurrences(reviewText, "Review window: 1 through 9"));
    assertEquals(1, occurrences(reviewText, "Review window: 10 through 20"));
    assertTrue(reviewText.contains("Affected operation orders: 1-3, 5, 7-8"));
    assertTrue(reviewText.contains("Affected operation orders: 10-11, 13"));
    assertFalse(reviewText.contains("…"));

    ByteArrayOutputStream strictVerificationOutput = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(strictVerificationOutput), fixedClock())
        .writeVerifyBookAttestation(verification, true, OutputMode.TEXT);
    String strictVerificationText = strictVerificationOutput.toString(StandardCharsets.UTF_8);

    assertEquals(2, occurrences(strictVerificationText, "Credential key ID: " + credentialKeyId));
    assertTrue(strictVerificationText.contains("Affected operation orders: 1-3, 5, 7-8"));
    assertTrue(strictVerificationText.contains("Affected operation orders: 10-11, 13"));
  }
}
