package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import dev.erst.fingrind.core.attestation.AttestationReviewFinding;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Transport contract for every public non-mutating attestation command and outcome. */
class CliAttestationTransportContractTest extends FinGrindCliTestSupport {
  private static final UUID BOOK_ID = UUID.fromString("1a507d74-df22-46d8-91de-c68ab48d43cf");
  private static final String OPERATION_HEAD = "a".repeat(64);

  @Test
  void commands_routeEveryValidAndInvalidAttestationResultAcrossTextAndJson() {
    AttestationWorkflow workflow =
        new AttestationWorkflow(
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                java.math.BigInteger.TWO,
                OPERATION_HEAD,
                List.of(),
                registry(java.math.BigInteger.TWO)),
            new AttestationReviewResult(
                BOOK_ID, java.math.BigInteger.TWO, List.of(reviewFinding())),
            new ExportAttestationReceiptResult.Exported(
                Path.of("receipts", "current.fgr"),
                BOOK_ID,
                java.math.BigInteger.TWO,
                OPERATION_HEAD,
                List.of("receipt retained outside the book directory")),
            new VerifyAttestationReceiptResult.Valid(
                BOOK_ID, java.math.BigInteger.TWO, List.of("independent copy verified")));

    ByteArrayOutputStream validVerificationOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(workflow, validVerificationOutput)
            .run(command("verify-book", "--require-clean-attestation", "--output", "text")));
    assertTrue(
        validVerificationOutput
            .toString(StandardCharsets.UTF_8)
            .contains("Book Attestation Valid"));

    ByteArrayOutputStream reviewOutput = new ByteArrayOutputStream();
    assertEquals(
        0, cli(workflow, reviewOutput).run(command("attestation-review", "--output", "json")));
    assertJsonContains(reviewOutput, "\"headOrder\":\"2\"");
    assertJsonContains(reviewOutput, "\"credentialKeyId\"");

    ByteArrayOutputStream exportOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(workflow, exportOutput)
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
        cli(workflow, receiptOutput)
            .run(
                command(
                    "verify-receipt",
                    "--receipt-file",
                    "receipts/current.fgr",
                    "--output",
                    "json")));
    assertJsonContains(receiptOutput, "independent copy verified");

    workflow.verifyBookResult =
        new VerifyBookAttestationResult.Invalid(
            AttestationVerificationFailure.PREIMAGE_INVALID.wireCode());
    ByteArrayOutputStream invalidBookOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        cli(workflow, invalidBookOutput).run(command("verify-book", "--output", "json")),
        () -> invalidBookOutput.toString(StandardCharsets.UTF_8));
    assertJsonContains(
        invalidBookOutput, AttestationVerificationFailure.PREIMAGE_INVALID.wireCode());

    workflow.verifyReceiptResult =
        new VerifyAttestationReceiptResult.Invalid(
            AttestationVerificationFailure.RECEIPT_INVALID.wireCode());
    ByteArrayOutputStream invalidReceiptOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        cli(workflow, invalidReceiptOutput)
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
            .contains(AttestationVerificationFailure.RECEIPT_INVALID.wireCode()));

    workflow.exportReceiptResult =
        new ExportAttestationReceiptResult.AuthorizationRejected(
            AttestationVerificationFailure.QUORUM_BELOW);
    ByteArrayOutputStream rejectedReceiptExportOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        cli(workflow, rejectedReceiptExportOutput)
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
                java.math.BigInteger.ZERO,
                OPERATION_HEAD,
                List.of(reviewFinding()),
                registry(java.math.BigInteger.ZERO)),
            new AttestationReviewResult(BOOK_ID, java.math.BigInteger.ZERO, List.of()),
            new ExportAttestationReceiptResult.Exported(
                Path.of("receipts", "unused.fgr"),
                BOOK_ID,
                java.math.BigInteger.ZERO,
                OPERATION_HEAD,
                List.of()),
            new VerifyAttestationReceiptResult.Valid(
                BOOK_ID, java.math.BigInteger.ZERO, List.of()));

    assertEquals(
        2,
        cli(workflow, new ByteArrayOutputStream())
            .run(command("verify-book", "--require-clean-attestation", "--output", "json")));
    assertEquals(
        0,
        cli(workflow, new ByteArrayOutputStream()).run(command("verify-book", "--output", "json")));
  }

  @Test
  void reviewDeclarations_areReadStrictlyAndSharedByVerifyAndReviewCommands() throws Exception {
    Path reviewFile = tempDirectory.resolve("compromise-reviews.json");
    Files.writeString(
        reviewFile,
        """
        {"compromiseReviews":[{"credentialKeyId":"%s","firstAffectedOrder":"0"}]}
        """
            .formatted("a".repeat(64)));

    VerifyBookAttestation verify =
        assertInstanceOf(
            VerifyBookAttestation.class,
            CliAttestationArguments.parseVerifyBookCommand(
                List.of(
                    "verify-book",
                    "--book-file",
                    "book.sqlite",
                    "--book-key-file",
                    "book.key",
                    "--attestation-review-file",
                    reviewFile.toString())));
    AttestationReview review =
        assertInstanceOf(
            AttestationReview.class,
            CliAttestationArguments.parseAttestationReviewCommand(
                List.of(
                    "attestation-review",
                    "--book-file",
                    "book.sqlite",
                    "--book-key-file",
                    "book.key",
                    "--attestation-review-file",
                    reviewFile.toString())));

    assertEquals(List.of(reviewDeclaration()), verify.compromiseReviews());
    assertEquals(verify.compromiseReviews(), review.compromiseReviews());
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationArguments.parseVerifyBookCommand(
                List.of(
                    "verify-book",
                    "--book-file",
                    "book.sqlite",
                    "--book-key-file",
                    "book.key",
                    "--attestation-review-file",
                    reviewFile.toString(),
                    "--attestation-review-file",
                    reviewFile.toString())));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationArguments.parseAttestationReviewCommand(
                List.of(
                    "attestation-review",
                    "--book-file",
                    "book.sqlite",
                    "--book-key-file",
                    "book.key",
                    "--attestation-review-file",
                    reviewFile.toString(),
                    "--attestation-review-file",
                    reviewFile.toString())));
  }

  @Test
  void reviewDeclarations_rejectNonCanonicalOrdersAndTrailingJson() throws Exception {
    Path reviewFile = tempDirectory.resolve("invalid-compromise-reviews.json");
    Files.writeString(
        reviewFile,
        """
        {"compromiseReviews":[{"credentialKeyId":"%s","firstAffectedOrder":"01"}]}
        """
            .formatted("a".repeat(64)));

    CliArgumentsException nonCanonicalOrder =
        assertThrows(CliArgumentsException.class, () -> parseVerifyBookWithReviewFile(reviewFile));
    assertEquals("--attestation-review-file", nonCanonicalOrder.argument());

    Files.writeString(
        reviewFile,
        """
        {"compromiseReviews":[]} {"trailing":true}
        """);
    CliArgumentsException trailingJson =
        assertThrows(CliArgumentsException.class, () -> parseVerifyBookWithReviewFile(reviewFile));
    assertEquals("--attestation-review-file", trailingJson.argument());
  }

  @Test
  void parsersRequireOneReceiptFileAndRejectDuplicateOrUnsupportedAttestationOptions() {
    assertInstanceOf(
        VerifyBookAttestation.class,
        CliAttestationArguments.parseVerifyBookCommand(
            List.of(
                "verify-book",
                "--book-file",
                "book.sqlite",
                "--book-key-file",
                "book.key",
                "--require-clean-attestation",
                "--output",
                "text")));
    assertInstanceOf(
        AttestationReview.class,
        CliAttestationArguments.parseAttestationReviewCommand(
            List.of(
                "attestation-review",
                "--book-file",
                "book.sqlite",
                "--book-key-file",
                "book.key",
                "--output",
                "json")));

    CliArgumentsException missingReceipt =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliAttestationArguments.parseExportReceiptCommand(
                    List.of(
                        "export-attestation-receipt",
                        "--book-file",
                        "book.sqlite",
                        "--book-key-file",
                        "book.key")));
    assertEquals("--receipt-file", missingReceipt.argument());

    CliArgumentsException duplicateReceipt =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliAttestationArguments.parseVerifyReceiptCommand(
                    List.of(
                        "verify-receipt",
                        "--book-file",
                        "book.sqlite",
                        "--book-key-file",
                        "book.key",
                        "--receipt-file",
                        "one.fgr",
                        "--receipt-file",
                        "two.fgr")));
    assertEquals("--receipt-file", duplicateReceipt.argument());

    CliArgumentsException unsupported =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliAttestationArguments.parseVerifyBookCommand(
                    List.of(
                        "verify-book",
                        "--book-file",
                        "book.sqlite",
                        "--book-key-file",
                        "book.key",
                        "--receipt-file",
                        "unexpected.fgr")));
    assertEquals("--receipt-file", unsupported.argument());
  }

  @Test
  void credentialSelections_areOptionalOnlyWhenEntirelyAbsentAndMustOtherwiseBeAligned() {
    CliAttestationCredentialArguments credentials = new CliAttestationCredentialArguments();
    assertFalse(credentials.apply("--not-an-attestation-option", List.<String>of().listIterator()));
    assertEquals(List.of(), credentials.resolveOptional());

    applyCredential(credentials, "--attestation-custodian", "file-pkcs8");
    applyCredential(credentials, "--attestation-principal-id", BOOK_ID.toString());
    applyCredential(credentials, "--attestation-key-file", "keys/principal.fgatk");
    applyCredential(credentials, "--attestation-passphrase-file", "keys/principal.passphrase");
    UUID secondPrincipal = UUID.fromString("4b4a38fa-cf41-4d53-afc1-8d4e6cdf438c");
    applyCredential(credentials, "--attestation-principal-id", secondPrincipal.toString());
    applyCredential(credentials, "--attestation-key-file", "keys/second-principal.fgatk");
    applyCredential(
        credentials, "--attestation-passphrase-file", "keys/second-principal.passphrase");
    assertEquals(
        List.of(BOOK_ID, secondPrincipal),
        credentials.resolveOptional().stream().map(source -> source.principalId()).toList());
    assertEquals(
        dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
        credentials.resolveOptional().getFirst().custodian());

    CliAttestationCredentialArguments incomplete = new CliAttestationCredentialArguments();
    applyCredential(incomplete, "--attestation-principal-id", BOOK_ID.toString());
    assertThrows(IllegalArgumentException.class, incomplete::resolveOptional);

    CliAttestationCredentialArguments noPrincipal = new CliAttestationCredentialArguments();
    applyCredential(noPrincipal, "--attestation-key-file", "keys/principal.fgatk");
    assertThrows(IllegalArgumentException.class, noPrincipal::resolveOptional);

    CliAttestationCredentialArguments passphraseWithoutPrincipal =
        new CliAttestationCredentialArguments();
    applyCredential(
        passphraseWithoutPrincipal, "--attestation-passphrase-file", "keys/principal.passphrase");
    assertThrows(IllegalArgumentException.class, passphraseWithoutPrincipal::resolveOptional);

    CliAttestationCredentialArguments keyWithoutPassphrase =
        new CliAttestationCredentialArguments();
    applyCredential(keyWithoutPassphrase, "--attestation-principal-id", BOOK_ID.toString());
    applyCredential(keyWithoutPassphrase, "--attestation-key-file", "keys/principal.fgatk");
    assertThrows(IllegalArgumentException.class, keyWithoutPassphrase::resolveOptional);

    CliAttestationCredentialArguments missingCustodian = new CliAttestationCredentialArguments();
    applyCredential(missingCustodian, "--attestation-principal-id", BOOK_ID.toString());
    applyCredential(missingCustodian, "--attestation-key-file", "keys/principal.fgatk");
    applyCredential(missingCustodian, "--attestation-passphrase-file", "keys/principal.passphrase");
    CliArgumentsException missingCustodianException =
        assertThrows(CliArgumentsException.class, missingCustodian::resolveOptional);
    assertEquals("--attestation-custodian", missingCustodianException.argument());

    CliAttestationCredentialArguments custodianOnly = new CliAttestationCredentialArguments();
    applyCredential(custodianOnly, "--attestation-custodian", "file-pkcs8");
    assertThrows(IllegalArgumentException.class, custodianOnly::resolveOptional);

    CliAttestationCredentialArguments duplicateCustodian = new CliAttestationCredentialArguments();
    applyCredential(duplicateCustodian, "--attestation-custodian", "file-pkcs8");
    assertThrows(
        CliArgumentsException.class,
        () -> applyCredential(duplicateCustodian, "--attestation-custodian", "file-pkcs8"));

    CliAttestationCredentialArguments sixCredentials = credentials(6);
    assertEquals(6, sixCredentials.resolveOptional().size());

    CliAttestationCredentialArguments tooMany = credentials(65);
    assertThrows(IllegalArgumentException.class, tooMany::resolveOptional);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliAttestationCredentialArguments.requirePresent(
                new BookAccess(
                    Path.of("book.sqlite"),
                    new BookAccess.PassphraseSource.KeyFile(Path.of("book.key")),
                    List.of())));
  }

  @Test
  void attestationVerificationPayload_keepsReviewStateDerivableAndCanonical() {
    CliAttestationJsonModels.VerifyBookPayload noReview =
        new CliAttestationJsonModels.VerifyBookPayload(
            BOOK_ID.toString(), "0", OPERATION_HEAD, false, List.of(), registryPayload());
    assertFalse(noReview.reviewRequired());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.VerifyBookPayload(
                BOOK_ID.toString(), "0", OPERATION_HEAD, true, List.of(), registryPayload()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.VerifyBookPayload(
                BOOK_ID.toString(),
                "0",
                OPERATION_HEAD,
                false,
                List.of(reviewFindingPayload()),
                registryPayload()));
  }

  @Test
  void successEnvelopes_omitEmptyArtifactsAndPreservePublishedArtifacts() {
    CliAttestationJsonModels.VerifyBookPayload payload =
        new CliAttestationJsonModels.VerifyBookPayload(
            BOOK_ID.toString(), "0", OPERATION_HEAD, false, List.of(), registryPayload());
    assertEquals(null, CliEnvelopeMapper.successEnvelope(payload, List.of()).artifacts());
    var artifacts =
        CliEnvelopeMapper.successEnvelope(
                payload,
                List.of(CliEnvelopeMapper.successArtifact("pdf", Path.of("reports", "book.pdf"))))
            .artifacts();
    assertEquals(1, Objects.requireNonNull(artifacts).size());
  }

  @Test
  void responseWriter_coversAttestationTextJsonAndUnsupportedCsvWithoutChangingResults() {
    CliBookReadResponseWriter writer =
        new CliBookReadResponseWriter(outputChannel(new ByteArrayOutputStream()), fixedClock());
    VerifyBookAttestationResult.Valid verification =
        new VerifyBookAttestationResult.Valid(
            BOOK_ID,
            java.math.BigInteger.TWO,
            OPERATION_HEAD,
            List.of(reviewFinding()),
            registry(java.math.BigInteger.TWO));
    AttestationReviewResult review =
        new AttestationReviewResult(BOOK_ID, java.math.BigInteger.TWO, List.of(reviewFinding()));
    ExportAttestationReceiptResult.Exported exported =
        new ExportAttestationReceiptResult.Exported(
            Path.of("receipts", "current.fgr"),
            BOOK_ID,
            java.math.BigInteger.TWO,
            OPERATION_HEAD,
            List.of("store independently"));
    VerifyAttestationReceiptResult.Valid receipt =
        new VerifyAttestationReceiptResult.Valid(
            BOOK_ID, java.math.BigInteger.TWO, List.of("receipt matches"));

    ByteArrayOutputStream verificationText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(verificationText), fixedClock())
        .writeVerifyBookAttestation(
            verification, false, dev.erst.fingrind.contract.protocol.OutputMode.TEXT);
    assertTrue(
        verificationText
            .toString(StandardCharsets.UTF_8)
            .contains("Book Attestation Valid — Review Required"));

    ByteArrayOutputStream strictVerificationJson = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(strictVerificationJson), fixedClock())
        .writeVerifyBookAttestation(
            verification, true, dev.erst.fingrind.contract.protocol.OutputMode.JSON);
    assertJsonContains(strictVerificationJson, "\"status\":\"rejected\"");
    assertJsonContains(strictVerificationJson, "\"code\":\"attestation-review-required\"");
    assertFalse(strictVerificationJson.toString(StandardCharsets.UTF_8).contains("\"payload\":"));

    ByteArrayOutputStream strictVerificationText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(strictVerificationText), fixedClock())
        .writeVerifyBookAttestation(
            verification, true, dev.erst.fingrind.contract.protocol.OutputMode.TEXT);
    String reviewRequiredText = strictVerificationText.toString(StandardCharsets.UTF_8);
    assertTrue(reviewRequiredText.contains("Book Attestation Review Required"));
    assertTrue(reviewRequiredText.contains("Book ID"));
    assertTrue(reviewRequiredText.contains("Head order"));
    assertTrue(reviewRequiredText.contains("Operation head"));
    assertTrue(reviewRequiredText.contains("Review findings"));
    assertTrue(reviewRequiredText.contains("credentialKeyId="));

    ByteArrayOutputStream reviewText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(reviewText), fixedClock())
        .writeAttestationReview(review, dev.erst.fingrind.contract.protocol.OutputMode.TEXT);
    assertTrue(reviewText.toString(StandardCharsets.UTF_8).contains("credentialKeyId="));

    ByteArrayOutputStream exportedJson = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(exportedJson), fixedClock())
        .writeExportAttestationReceipt(
            exported, dev.erst.fingrind.contract.protocol.OutputMode.JSON);
    assertJsonContains(exportedJson, "\"receiptFile\"");
    assertJsonContains(exportedJson, "\"store independently\"");

    ByteArrayOutputStream exportedText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(exportedText), fixedClock())
        .writeExportAttestationReceipt(
            exported, dev.erst.fingrind.contract.protocol.OutputMode.TEXT);
    assertTrue(
        exportedText.toString(StandardCharsets.UTF_8).contains("Attestation Receipt Exported"));

    ByteArrayOutputStream authorizationRejectedJson = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(authorizationRejectedJson), fixedClock())
        .writeExportAttestationReceipt(
            new ExportAttestationReceiptResult.AuthorizationRejected(
                AttestationVerificationFailure.QUORUM_BELOW),
            dev.erst.fingrind.contract.protocol.OutputMode.JSON);
    assertJsonContains(authorizationRejectedJson, "\"status\":\"rejected\"");
    assertJsonContains(authorizationRejectedJson, "\"code\":\"attestation-quorum-below\"");

    ByteArrayOutputStream receiptText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(receiptText), fixedClock())
        .writeVerifyAttestationReceipt(
            receipt, dev.erst.fingrind.contract.protocol.OutputMode.TEXT);
    assertTrue(receiptText.toString(StandardCharsets.UTF_8).contains("Attestation Receipt Valid"));

    ByteArrayOutputStream emptyFindingsText = new ByteArrayOutputStream();
    CliBookReadResponseWriter emptyFindingsWriter =
        new CliBookReadResponseWriter(outputChannel(emptyFindingsText), fixedClock());
    emptyFindingsWriter.writeAttestationReview(
        new AttestationReviewResult(BOOK_ID, java.math.BigInteger.ZERO, List.of()),
        dev.erst.fingrind.contract.protocol.OutputMode.TEXT);
    emptyFindingsWriter.writeExportAttestationReceipt(
        new ExportAttestationReceiptResult.Exported(
            Path.of("receipts", "empty.fgr"),
            BOOK_ID,
            java.math.BigInteger.ZERO,
            OPERATION_HEAD,
            List.of()),
        dev.erst.fingrind.contract.protocol.OutputMode.TEXT);
    emptyFindingsWriter.writeVerifyAttestationReceipt(
        new VerifyAttestationReceiptResult.Valid(BOOK_ID, java.math.BigInteger.ZERO, List.of()),
        dev.erst.fingrind.contract.protocol.OutputMode.TEXT);
    assertTrue(emptyFindingsText.toString(StandardCharsets.UTF_8).contains("(none)"));

    AttestationReviewFinding boundedFinding =
        new AttestationReviewFinding(
            new AttestationCompromiseReview(
                "b".repeat(64), java.math.BigInteger.ONE, java.math.BigInteger.TWO),
            java.math.BigInteger.TWO);
    ByteArrayOutputStream boundedJson = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(boundedJson), fixedClock())
        .writeVerifyBookAttestation(
            new VerifyBookAttestationResult.Valid(
                BOOK_ID,
                java.math.BigInteger.TWO,
                OPERATION_HEAD,
                List.of(boundedFinding),
                registry(java.math.BigInteger.TWO)),
            false,
            dev.erst.fingrind.contract.protocol.OutputMode.JSON);
    assertJsonContains(boundedJson, "\"lastAffectedOrder\":\"2\"");

    AttestationReviewFinding openEndedFinding =
        new AttestationReviewFinding(
            new AttestationCompromiseReview("c".repeat(64), java.math.BigInteger.ONE, null),
            java.math.BigInteger.TWO);
    ByteArrayOutputStream openEndedJson = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(openEndedJson), fixedClock())
        .writeAttestationReview(
            new AttestationReviewResult(
                BOOK_ID, java.math.BigInteger.TWO, List.of(openEndedFinding)),
            dev.erst.fingrind.contract.protocol.OutputMode.JSON);
    assertJsonContains(openEndedJson, "\"lastAffectedOrder\":null");

    ByteArrayOutputStream boundedText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(boundedText), fixedClock())
        .writeAttestationReview(
            new AttestationReviewResult(BOOK_ID, java.math.BigInteger.TWO, List.of(boundedFinding)),
            dev.erst.fingrind.contract.protocol.OutputMode.TEXT);
    assertTrue(boundedText.toString(StandardCharsets.UTF_8).contains("lastAffectedOrder=2"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer.writeVerifyBookAttestation(
                verification, false, dev.erst.fingrind.contract.protocol.OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer.writeVerifyBookAttestation(
                verification, true, dev.erst.fingrind.contract.protocol.OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer.writeAttestationReview(
                review, dev.erst.fingrind.contract.protocol.OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer.writeExportAttestationReceipt(
                exported, dev.erst.fingrind.contract.protocol.OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer.writeVerifyAttestationReceipt(
                receipt, dev.erst.fingrind.contract.protocol.OutputMode.CSV));
  }

  private static void applyCredential(
      CliAttestationCredentialArguments credentials, String option, String value) {
    ListIterator<String> iterator = List.of(value).listIterator();
    assertTrue(credentials.apply(option, iterator));
  }

  private static CliAttestationCredentialArguments credentials(int count) {
    CliAttestationCredentialArguments credentials = new CliAttestationCredentialArguments();
    applyCredential(credentials, "--attestation-custodian", "file-pkcs8");
    for (int index = 0; index < count; index++) {
      applyCredential(
          credentials,
          "--attestation-principal-id",
          "00000000-0000-4000-8000-%012d".formatted(index));
      applyCredential(
          credentials, "--attestation-key-file", "keys/principal-%d.fgatk".formatted(index));
      applyCredential(
          credentials,
          "--attestation-passphrase-file",
          "keys/principal-%d.passphrase".formatted(index));
    }
    return credentials;
  }

  private FinGrindCli cli(AttestationWorkflow workflow, ByteArrayOutputStream outputStream) {
    return cli(
        new ByteArrayInputStream(new byte[0]),
        utf8PrintStream(outputStream),
        fixedClock(),
        workflow);
  }

  private static CliOutputChannel outputChannel(ByteArrayOutputStream outputStream) {
    return new CliOutputChannel(utf8PrintStream(outputStream));
  }

  private static String[] command(String operation, String... tail) {
    String[] arguments = new String[tail.length + 5];
    arguments[0] = operation;
    arguments[1] = "--book-file";
    arguments[2] = "books/current.sqlite";
    arguments[3] = "--book-key-file";
    arguments[4] = "keys/current.key";
    System.arraycopy(tail, 0, arguments, 5, tail.length);
    return arguments;
  }

  private static VerifyBookAttestation parseVerifyBookWithReviewFile(Path reviewFile) {
    return assertInstanceOf(
        VerifyBookAttestation.class,
        CliAttestationArguments.parseVerifyBookCommand(
            List.of(
                "verify-book",
                "--book-file",
                "book.sqlite",
                "--book-key-file",
                "book.key",
                "--attestation-review-file",
                reviewFile.toString())));
  }

  private static AttestationReviewFinding reviewFinding() {
    return new AttestationReviewFinding(reviewDeclaration(), java.math.BigInteger.ONE);
  }

  private static AttestationCompromiseReview reviewDeclaration() {
    return new AttestationCompromiseReview("a".repeat(64), java.math.BigInteger.ZERO, null);
  }

  private static CliAttestationJsonModels.AttestationReviewFindingPayload reviewFindingPayload() {
    return new CliAttestationJsonModels.AttestationReviewFindingPayload(
        "a".repeat(64), "0", null, "1");
  }

  private static AttestationRegistryInspection registry(java.math.BigInteger headOrder) {
    return new AttestationRegistryInspection(
        BOOK_ID, headOrder, OPERATION_HEAD, List.of(), List.of(), List.of(), List.of());
  }

  private static CliAttestationJsonModels.AttestationRegistryPayload registryPayload() {
    return new CliAttestationJsonModels.AttestationRegistryPayload(
        List.of(), List.of(), List.of(), List.of());
  }

  /** Configurable workflow double for the attestation transport matrix. */
  private static final class AttestationWorkflow extends CliBookWorkflowAdapter {
    private VerifyBookAttestationResult verifyBookResult;
    private final AttestationReviewResult reviewResult;
    private ExportAttestationReceiptResult exportReceiptResult;
    private VerifyAttestationReceiptResult verifyReceiptResult;

    private AttestationWorkflow(
        VerifyBookAttestationResult verifyBookResult,
        AttestationReviewResult reviewResult,
        ExportAttestationReceiptResult exportReceiptResult,
        VerifyAttestationReceiptResult verifyReceiptResult) {
      this.verifyBookResult = verifyBookResult;
      this.reviewResult = reviewResult;
      this.exportReceiptResult = exportReceiptResult;
      this.verifyReceiptResult = verifyReceiptResult;
    }

    @Override
    public ContractDecision<VerifyBookAttestationResult> verifyBookAttestation(
        BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews) {
      return ContractDecision.accepted(verifyBookResult);
    }

    @Override
    public ContractDecision<AttestationReviewResult> reviewAttestation(
        BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews) {
      return ContractDecision.accepted(reviewResult);
    }

    @Override
    public ContractDecision<ExportAttestationReceiptResult> exportAttestationReceipt(
        BookAccess bookAccess, Path receiptFilePath) {
      return ContractDecision.accepted(exportReceiptResult);
    }

    @Override
    public ContractDecision<VerifyAttestationReceiptResult> verifyAttestationReceipt(
        BookAccess bookAccess, Path receiptFilePath) {
      return ContractDecision.accepted(verifyReceiptResult);
    }
  }
}
