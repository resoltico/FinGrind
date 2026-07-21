package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    assertTrue(!credentials.apply("--not-an-attestation-option", List.<String>of().listIterator()));
    assertEquals(List.of(), credentials.resolveOptional());

    applyCredential(credentials, "--attestation-principal-id", BOOK_ID.toString());
    applyCredential(credentials, "--attestation-key-file", "keys/principal.fgatk");
    applyCredential(credentials, "--attestation-passphrase-file", "keys/principal.passphrase");
    assertEquals(1, credentials.resolveOptional().size());

    CliAttestationCredentialArguments incomplete = new CliAttestationCredentialArguments();
    applyCredential(incomplete, "--attestation-principal-id", BOOK_ID.toString());
    assertThrows(IllegalArgumentException.class, incomplete::resolveOptional);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliAttestationCredentialArguments.requirePresent(
                new BookAccess(
                    Path.of("book.sqlite"),
                    new BookAccess.PassphraseSource.KeyFile(Path.of("book.key")),
                    List.of())));
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
