package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Shared deterministic fixtures for attestation transport contract tests. */
abstract class CliAttestationTransportFixtures extends CliWorkflowFixtureSupport {
  protected static final UUID BOOK_ID = UUID.fromString("1a507d74-df22-46d8-91de-c68ab48d43cf");
  protected static final String OPERATION_HEAD = "a".repeat(64);
  protected static final String PREVIOUS_HEAD = "b".repeat(64);

  protected static FinGrindCli attestationCli(
      CliBookWorkflow workflow, ByteArrayOutputStream outputStream) {
    return cli(
        new ByteArrayInputStream(new byte[0]),
        utf8PrintStream(outputStream),
        fixedClock(),
        workflow);
  }

  protected static CliOutputChannel outputChannel(ByteArrayOutputStream outputStream) {
    return new CliOutputChannel(utf8PrintStream(outputStream));
  }

  protected static String[] command(String operation, String... tail) {
    String[] arguments = new String[tail.length + 5];
    arguments[0] = operation;
    arguments[1] = "--book-file";
    arguments[2] = "books/current.sqlite";
    arguments[3] = "--book-key-file";
    arguments[4] = "keys/current.key";
    System.arraycopy(tail, 0, arguments, 5, tail.length);
    return arguments;
  }

  protected static AttestationReviewFinding reviewFinding() {
    return new AttestationReviewFinding(reviewDeclaration(), BigInteger.ONE);
  }

  protected static AttestationReviewResult.Valid reviewResult(
      BigInteger headOrder, List<AttestationReviewFinding> findings) {
    return new AttestationReviewResult.Valid(BOOK_ID, headOrder, OPERATION_HEAD, findings);
  }

  protected static CliAttestationJsonModels.AttestationHeadPayload verifiedHead(
      BigInteger operationOrder) {
    return new CliAttestationJsonModels.AttestationHeadPayload(
        operationOrder.toString(), OPERATION_HEAD);
  }

  protected static AttestationCompromiseReview reviewDeclaration() {
    return new AttestationCompromiseReview("a".repeat(64), BigInteger.ZERO, null);
  }

  protected static CliAttestationJsonModels.AttestationReviewFindingPayload reviewFindingPayload() {
    return new CliAttestationJsonModels.AttestationReviewFindingPayload(
        "a".repeat(64), "0", null, "1");
  }

  protected static AttestationRegistryInspection registry(BigInteger headOrder) {
    return new AttestationRegistryInspection(
        BOOK_ID, headOrder, OPERATION_HEAD, List.of(), List.of(), List.of(), List.of());
  }

  protected static CliAttestationJsonModels.AttestationRegistryPayload registryPayload() {
    return new CliAttestationJsonModels.AttestationRegistryPayload(
        List.of(), List.of(), List.of(), List.of());
  }

  protected static void assertTextContains(
      ByteArrayOutputStream output, String expectedMessage, String expectedHint) {
    String text = output.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains(expectedMessage), text);
    assertTrue(text.contains(expectedHint), text);
  }

  protected static int occurrences(String text, String value) {
    int count = 0;
    int index = text.indexOf(value);
    while (index >= 0) {
      count++;
      index = text.indexOf(value, index + value.length());
    }
    return count;
  }

  /** Configurable workflow double for command-routing transport tests. */
  protected static final class AttestationWorkflow extends CliBookWorkflowAdapter {
    private VerifyBookAttestationResult verifyBookResult;
    private AttestationReviewResult reviewResult;
    private ExportAttestationReceiptResult exportReceiptResult;
    private VerifyAttestationReceiptResult verifyReceiptResult;

    AttestationWorkflow(
        VerifyBookAttestationResult verifyBookResult,
        AttestationReviewResult reviewResult,
        ExportAttestationReceiptResult exportReceiptResult,
        VerifyAttestationReceiptResult verifyReceiptResult) {
      this.verifyBookResult = verifyBookResult;
      this.reviewResult = reviewResult;
      this.exportReceiptResult = exportReceiptResult;
      this.verifyReceiptResult = verifyReceiptResult;
    }

    void setVerifyBookResult(VerifyBookAttestationResult result) {
      verifyBookResult = result;
    }

    void setReviewResult(AttestationReviewResult result) {
      reviewResult = result;
    }

    void setExportReceiptResult(ExportAttestationReceiptResult result) {
      exportReceiptResult = result;
    }

    void setVerifyReceiptResult(VerifyAttestationReceiptResult result) {
      verifyReceiptResult = result;
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
        BookAccess bookAccess, java.nio.file.Path receiptFilePath) {
      return ContractDecision.accepted(exportReceiptResult);
    }

    @Override
    public ContractDecision<VerifyAttestationReceiptResult> verifyAttestationReceipt(
        BookAccess bookAccess, java.nio.file.Path receiptFilePath) {
      return ContractDecision.accepted(verifyReceiptResult);
    }
  }
}
