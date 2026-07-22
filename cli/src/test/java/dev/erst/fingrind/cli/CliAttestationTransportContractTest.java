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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
                BOOK_ID, java.math.BigInteger.TWO, OPERATION_HEAD, List.of()),
            new AttestationReviewResult(
                BOOK_ID, java.math.BigInteger.TWO, List.of("retained backup overdue")),
            new ExportAttestationReceiptResult(
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
    assertJsonContains(reviewOutput, "retained backup overdue");

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
  }

  @Test
  void verifyBookRequireClean_changesOnlyTheExitStatusWhenReviewIsRequired() {
    AttestationWorkflow workflow =
        new AttestationWorkflow(
            new VerifyBookAttestationResult.Valid(
                BOOK_ID, java.math.BigInteger.ZERO, OPERATION_HEAD, List.of("review this")),
            new AttestationReviewResult(BOOK_ID, java.math.BigInteger.ZERO, List.of()),
            new ExportAttestationReceiptResult(
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
  void credentialTriples_areOptionalOnlyWhenEntirelyAbsentAndMustOtherwiseBeAligned() {
    CliAttestationCredentialArguments credentials = new CliAttestationCredentialArguments();
    assertFalse(credentials.apply("--not-an-attestation-option", List.<String>of().listIterator()));
    assertEquals(List.of(), credentials.resolveOptional());

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

    CliAttestationCredentialArguments tooMany = new CliAttestationCredentialArguments();
    for (int index = 0; index < 6; index++) {
      applyCredential(
          tooMany, "--attestation-principal-id", "00000000-0000-4000-8000-%012d".formatted(index));
      applyCredential(
          tooMany, "--attestation-key-file", "keys/principal-%d.fgatk".formatted(index));
      applyCredential(
          tooMany,
          "--attestation-passphrase-file",
          "keys/principal-%d.passphrase".formatted(index));
    }
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
            BOOK_ID.toString(), "0", OPERATION_HEAD, false, List.of());
    assertFalse(noReview.reviewRequired());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.VerifyBookPayload(
                BOOK_ID.toString(), "0", OPERATION_HEAD, true, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.VerifyBookPayload(
                BOOK_ID.toString(), "0", OPERATION_HEAD, false, List.of("retain receipt")));
  }

  @Test
  void successEnvelopes_omitEmptyArtifactsAndPreservePublishedArtifacts() {
    CliAttestationJsonModels.VerifyBookPayload payload =
        new CliAttestationJsonModels.VerifyBookPayload(
            BOOK_ID.toString(), "0", OPERATION_HEAD, false, List.of());
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
            BOOK_ID, java.math.BigInteger.TWO, OPERATION_HEAD, List.of("review this chain"));
    AttestationReviewResult review =
        new AttestationReviewResult(BOOK_ID, java.math.BigInteger.TWO, List.of("retain receipt"));
    ExportAttestationReceiptResult exported =
        new ExportAttestationReceiptResult(
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
            verification, dev.erst.fingrind.contract.protocol.OutputMode.TEXT);
    assertTrue(
        verificationText
            .toString(StandardCharsets.UTF_8)
            .contains("Book Attestation Valid — Review Required"));

    ByteArrayOutputStream reviewText = new ByteArrayOutputStream();
    new CliBookReadResponseWriter(outputChannel(reviewText), fixedClock())
        .writeAttestationReview(review, dev.erst.fingrind.contract.protocol.OutputMode.TEXT);
    assertTrue(reviewText.toString(StandardCharsets.UTF_8).contains("retain receipt"));

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
        new ExportAttestationReceiptResult(
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

    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer.writeVerifyBookAttestation(
                verification, dev.erst.fingrind.contract.protocol.OutputMode.CSV));
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

  /** Configurable workflow double for the attestation transport matrix. */
  private static final class AttestationWorkflow extends CliBookWorkflowAdapter {
    private VerifyBookAttestationResult verifyBookResult;
    private final AttestationReviewResult reviewResult;
    private final ExportAttestationReceiptResult exportReceiptResult;
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
        BookAccess bookAccess) {
      return ContractDecision.accepted(verifyBookResult);
    }

    @Override
    public ContractDecision<AttestationReviewResult> reviewAttestation(BookAccess bookAccess) {
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
