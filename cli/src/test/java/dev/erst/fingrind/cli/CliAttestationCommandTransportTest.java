package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Verifies command routing for public non-mutating attestation operations. */
class CliAttestationCommandTransportTest extends CliAttestationTransportFixtures {
  @Test
  void commands_routeEveryValidAndInvalidAttestationResultAcrossTextAndJson() {
    AttestationWorkflow workflow =
        new AttestationWorkflow(
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                BigInteger.TWO,
                OPERATION_HEAD,
                PREVIOUS_HEAD,
                List.of(),
                registry(BigInteger.TWO)),
            reviewResult(BigInteger.TWO, List.of(reviewFinding())),
            new ExportAttestationReceiptResult.Exported(
                new ArtifactPublicationResult(
                    Path.of("resolved-receipts", "current.fgr"),
                    new ArtifactPublicationRetention(
                        Path.of("resolved-receipts", ".current.fgr-stage"))),
                BOOK_ID,
                BigInteger.TWO,
                OPERATION_HEAD,
                List.of("receipt retained outside the book directory")),
            new VerifyAttestationReceiptResult.Valid(
                Path.of("resolved-receipts", "current.fgr"),
                BOOK_ID,
                BigInteger.TWO,
                OPERATION_HEAD,
                List.of("independent copy verified")));

    ByteArrayOutputStream validVerificationOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        attestationCli(workflow, validVerificationOutput)
            .run(command("verify-book", "--require-clean-attestation", "--output", "text")));
    assertTrue(
        validVerificationOutput
            .toString(StandardCharsets.UTF_8)
            .contains("Book Attestation Valid"));
    assertTextContains(validVerificationOutput, "Previous attestation head", PREVIOUS_HEAD);

    ByteArrayOutputStream reviewOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        attestationCli(workflow, reviewOutput)
            .run(command("attestation-review", "--output", "json")));
    assertJsonContains(reviewOutput, "\"verifiedAttestationHead\"");
    assertJsonContains(reviewOutput, "\"operationOrder\":\"2\"");
    assertJsonContains(reviewOutput, "\"operationHead\":\"" + OPERATION_HEAD + "\"");
    assertJsonContains(reviewOutput, "\"credentialKeyId\"");

    ByteArrayOutputStream exportOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        attestationCli(workflow, exportOutput)
            .run(
                command(
                    "export-attestation-receipt",
                    "--receipt-file",
                    "receipts/current.fgr",
                    "--output",
                    "text")));
    assertTrue(
        exportOutput.toString(StandardCharsets.UTF_8).contains("Attestation Receipt Exported"));

    ByteArrayOutputStream receiptOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        attestationCli(workflow, receiptOutput)
            .run(
                command(
                    "verify-receipt",
                    "--receipt-file",
                    "receipts/current.fgr",
                    "--output",
                    "json")));
    assertJsonContains(receiptOutput, "independent copy verified");
    assertJsonContains(
        receiptOutput,
        "\"receiptFile\":\""
            + CliPublicPaths.absoluteValue(Path.of("resolved-receipts", "current.fgr"))
            + "\"");
    var receiptPayload = new ObjectMapper().readTree(receiptOutput.toByteArray()).path("payload");
    assertEquals(
        "2", receiptPayload.path("receiptAttestationAnchor").path("operationOrder").stringValue());
    assertEquals(
        OPERATION_HEAD,
        receiptPayload.path("receiptAttestationAnchor").path("operationHead").stringValue());
    assertFalse(receiptPayload.has("operationOrder"));
    assertFalse(receiptPayload.has("operationHead"));

    ByteArrayOutputStream receiptTextOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        attestationCli(workflow, receiptTextOutput)
            .run(
                command(
                    "verify-receipt",
                    "--receipt-file",
                    "receipts/current.fgr",
                    "--output",
                    "text")));
    assertTextContains(
        receiptTextOutput,
        "Receipt file",
        CliTextDisplay.path(Path.of("resolved-receipts", "current.fgr")));
    assertTextContains(receiptTextOutput, "Attestation order", "2");
    assertTextContains(receiptTextOutput, "Attestation head", OPERATION_HEAD);

    workflow.setVerifyBookResult(
        new VerifyBookAttestationResult.Invalid(
            AttestationVerificationFailure.PREIMAGE_INVALID.wireCode()));
    ByteArrayOutputStream invalidBookOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        attestationCli(workflow, invalidBookOutput).run(command("verify-book", "--output", "json")),
        () -> invalidBookOutput.toString(StandardCharsets.UTF_8));
    assertJsonContains(
        invalidBookOutput, AttestationVerificationFailure.PREIMAGE_INVALID.wireCode());
    assertJsonContains(
        invalidBookOutput,
        AttestationVerificationFailure.PREIMAGE_INVALID
            .verificationDiagnostic(OperationId.VERIFY_BOOK)
            .message());
    assertJsonContains(
        invalidBookOutput,
        AttestationVerificationFailure.PREIMAGE_INVALID
            .verificationDiagnostic(OperationId.VERIFY_BOOK)
            .hint());

    workflow.setReviewResult(
        new AttestationReviewResult.Invalid(
            AttestationVerificationFailure.PREVIOUS_HEAD_INVALID.wireCode()));
    ByteArrayOutputStream invalidReviewOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        attestationCli(workflow, invalidReviewOutput)
            .run(command("attestation-review", "--output", "json")),
        () -> invalidReviewOutput.toString(StandardCharsets.UTF_8));
    assertJsonContains(
        invalidReviewOutput,
        AttestationVerificationFailure.PREVIOUS_HEAD_INVALID
            .verificationDiagnostic(OperationId.ATTESTATION_REVIEW)
            .message());
    assertJsonContains(
        invalidReviewOutput,
        AttestationVerificationFailure.PREVIOUS_HEAD_INVALID
            .verificationDiagnostic(OperationId.ATTESTATION_REVIEW)
            .hint());

    workflow.setExportReceiptResult(
        new ExportAttestationReceiptResult.VerificationRejected(
            AttestationVerificationFailure.CAPABILITY_INVALID));
    ByteArrayOutputStream invalidReceiptExportOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        attestationCli(workflow, invalidReceiptExportOutput)
            .run(
                command(
                    "export-attestation-receipt",
                    "--receipt-file",
                    "receipts/current.fgr",
                    "--output",
                    "text")),
        () -> invalidReceiptExportOutput.toString(StandardCharsets.UTF_8));
    assertTextContains(
        invalidReceiptExportOutput,
        AttestationVerificationFailure.CAPABILITY_INVALID
            .verificationDiagnostic(OperationId.EXPORT_ATTESTATION_RECEIPT)
            .message(),
        AttestationVerificationFailure.CAPABILITY_INVALID
            .verificationDiagnostic(OperationId.EXPORT_ATTESTATION_RECEIPT)
            .hint());

    workflow.setVerifyReceiptResult(
        new VerifyAttestationReceiptResult.Invalid(
            AttestationVerificationFailure.SIGNATURE_INVALID.wireCode()));
    ByteArrayOutputStream invalidReceiptOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        attestationCli(workflow, invalidReceiptOutput)
            .run(
                command(
                    "verify-receipt",
                    "--receipt-file",
                    "receipts/current.fgr",
                    "--output",
                    "text")));
    assertTrue(
        invalidReceiptOutput
            .toString(StandardCharsets.UTF_8)
            .contains(AttestationVerificationFailure.SIGNATURE_INVALID.wireCode()));
    assertTextContains(
        invalidReceiptOutput,
        AttestationVerificationFailure.SIGNATURE_INVALID
            .verificationDiagnostic(OperationId.VERIFY_RECEIPT)
            .message(),
        AttestationVerificationFailure.SIGNATURE_INVALID
            .verificationDiagnostic(OperationId.VERIFY_RECEIPT)
            .hint());

    workflow.setExportReceiptResult(
        new ExportAttestationReceiptResult.AuthorizationRejected(
            AttestationVerificationFailure.QUORUM_BELOW));
    ByteArrayOutputStream rejectedReceiptExportOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        attestationCli(workflow, rejectedReceiptExportOutput)
            .run(
                command(
                    "export-attestation-receipt",
                    "--receipt-file",
                    "receipts/current.fgr",
                    "--output",
                    "json")));
    assertJsonContains(rejectedReceiptExportOutput, "\"status\":\"rejected\"");
    assertJsonContains(rejectedReceiptExportOutput, "\"code\":\"attestation-quorum-below\"");
  }

  @Test
  void verifyBookRequireClean_changesOnlyTheExitStatusWhenReviewIsRequired() {
    AttestationWorkflow workflow =
        new AttestationWorkflow(
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                BigInteger.ONE,
                OPERATION_HEAD,
                PREVIOUS_HEAD,
                List.of(reviewFinding()),
                registry(BigInteger.ONE)),
            reviewResult(BigInteger.ONE, List.of()),
            new ExportAttestationReceiptResult.Exported(
                new ArtifactPublicationResult(
                    Path.of("receipts", "unused.fgr"),
                    new ArtifactPublicationRetention(Path.of("receipts", ".unused.fgr-stage"))),
                BOOK_ID,
                BigInteger.ONE,
                OPERATION_HEAD,
                List.of()),
            new VerifyAttestationReceiptResult.Valid(
                Path.of("resolved-receipts", "unused.fgr"),
                BOOK_ID,
                BigInteger.ONE,
                OPERATION_HEAD,
                List.of()));

    assertEquals(
        2,
        attestationCli(workflow, new ByteArrayOutputStream())
            .run(command("verify-book", "--require-clean-attestation", "--output", "json")));
    assertEquals(
        0,
        attestationCli(workflow, new ByteArrayOutputStream())
            .run(command("verify-book", "--output", "json")));
  }
}
