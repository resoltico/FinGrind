package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import dev.erst.fingrind.core.attestation.AttestationReviewWindowException;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Verifies review declaration admission and review-window failure transport. */
class CliAttestationReviewTransportTest extends CliAttestationTransportFixtures {
  @Test
  void reviewWindowOutsideVerifiedHead_isTypedErrorRatherThanAnInternalFailure() {
    AttestationCompromiseReview review =
        new AttestationCompromiseReview("a".repeat(64), BigInteger.TWO, null);
    AttestationReviewWindowException failure =
        new AttestationReviewWindowException(review, BigInteger.ONE);

    ByteArrayOutputStream verificationOutput = new ByteArrayOutputStream();
    assertEquals(
        1,
        attestationCli(new ReviewWindowFailureWorkflow(failure), verificationOutput)
            .run(command("verify-book", "--output", "json")));
    String verificationJson = verificationOutput.toString(StandardCharsets.UTF_8);
    assertReviewWindowFailureEnvelope(verificationOutput, review, BigInteger.ONE);
    assertFalse(verificationJson.contains("internal-error"), verificationJson);
    assertFalse(verificationJson.contains("fg-internal-"), verificationJson);

    ByteArrayOutputStream reviewJsonOutput = new ByteArrayOutputStream();
    assertEquals(
        1,
        attestationCli(new ReviewWindowFailureWorkflow(failure), reviewJsonOutput)
            .run(command("attestation-review", "--output", "json")));
    assertReviewWindowFailureEnvelope(reviewJsonOutput, review, BigInteger.ONE);

    ByteArrayOutputStream reviewTextOutput = new ByteArrayOutputStream();
    assertEquals(
        1,
        attestationCli(new ReviewWindowFailureWorkflow(failure), reviewTextOutput)
            .run(command("attestation-review", "--output", "text")));
    assertTextContains(
        reviewTextOutput,
        "The declared compromise-review window is not contained by the authenticated book head.",
        "Set firstAffectedOrder and any lastAffectedOrder no higher than verifiedHeadOrder");
    assertTextContains(reviewTextOutput, "Credential key ID", review.credentialKeyId());
    assertTextContains(
        reviewTextOutput, "First affected order", review.firstAffectedOrder().toString());
    assertTextContains(reviewTextOutput, "Last affected order", "(through verified head)");
    assertTextContains(reviewTextOutput, "Verified attestation order", BigInteger.ONE.toString());
    assertFalse(
        reviewTextOutput.toString(StandardCharsets.UTF_8).contains("Verified head order"),
        reviewTextOutput.toString(StandardCharsets.UTF_8));
  }

  @Test
  void boundedReviewWindowOutsideVerifiedHead_preservesItsDeclaredLastAffectedOrder() {
    AttestationCompromiseReview review =
        new AttestationCompromiseReview("a".repeat(64), BigInteger.TWO, BigInteger.valueOf(3));
    AttestationReviewWindowException failure =
        new AttestationReviewWindowException(review, BigInteger.ONE);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    assertEquals(
        1,
        attestationCli(new ReviewWindowFailureWorkflow(failure), output)
            .run(command("verify-book", "--output", "json")));

    JsonNode details = new ObjectMapper().readTree(output.toByteArray()).path("details");
    assertEquals("2", details.path("firstAffectedOrder").stringValue());
    assertEquals("3", details.path("lastAffectedOrder").stringValue());
    assertEquals("1", details.path("verifiedHeadOrder").stringValue());
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

  private static void assertReviewWindowFailureEnvelope(
      ByteArrayOutputStream output,
      AttestationCompromiseReview review,
      BigInteger verifiedHeadOrder) {
    JsonNode envelope = new ObjectMapper().readTree(output.toByteArray());
    assertEquals(
        "error", envelope.path("status").stringValue(), output.toString(StandardCharsets.UTF_8));
    assertEquals(
        ContractErrors.Descriptor.ATTESTATION_REVIEW_WINDOW_EXCEEDS_HEAD.code(),
        envelope.path("code").stringValue(),
        output.toString(StandardCharsets.UTF_8));
    assertEquals(
        "domain-semantic",
        envelope.path("category").stringValue(),
        output.toString(StandardCharsets.UTF_8));
    assertEquals(
        "The declared compromise-review window is not contained by the authenticated book head.",
        envelope.path("message").stringValue(),
        output.toString(StandardCharsets.UTF_8));
    assertEquals(
        "Set firstAffectedOrder and any lastAffectedOrder no higher than verifiedHeadOrder, or omit lastAffectedOrder to review through that head.",
        envelope.path("hint").stringValue(),
        output.toString(StandardCharsets.UTF_8));
    assertEquals(
        "--attestation-review-file",
        envelope.path("argument").stringValue(),
        output.toString(StandardCharsets.UTF_8));
    assertFalse(envelope.has("payload"), output.toString(StandardCharsets.UTF_8));

    JsonNode details = envelope.path("details");
    assertEquals(
        Set.of("credentialKeyId", "firstAffectedOrder", "lastAffectedOrder", "verifiedHeadOrder"),
        Set.copyOf(details.properties().stream().map(entry -> entry.getKey()).toList()),
        output.toString(StandardCharsets.UTF_8));
    assertEquals(
        review.credentialKeyId(),
        details.path("credentialKeyId").stringValue(),
        output.toString(StandardCharsets.UTF_8));
    assertEquals(
        review.firstAffectedOrder().toString(),
        details.path("firstAffectedOrder").stringValue(),
        output.toString(StandardCharsets.UTF_8));
    assertTrue(details.has("lastAffectedOrder"), output.toString(StandardCharsets.UTF_8));
    assertTrue(details.path("lastAffectedOrder").isNull(), output.toString(StandardCharsets.UTF_8));
    assertEquals(
        verifiedHeadOrder.toString(),
        details.path("verifiedHeadOrder").stringValue(),
        output.toString(StandardCharsets.UTF_8));
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

  /** Workflow double that models the dedicated core review-window refusal. */
  private static final class ReviewWindowFailureWorkflow extends CliBookWorkflowAdapter {
    private final AttestationReviewWindowException failure;

    private ReviewWindowFailureWorkflow(AttestationReviewWindowException failure) {
      this.failure = Objects.requireNonNull(failure, "failure");
    }

    @Override
    public ContractDecision<VerifyBookAttestationResult> verifyBookAttestation(
        BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews) {
      throw failure;
    }

    @Override
    public ContractDecision<AttestationReviewResult> reviewAttestation(
        BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews) {
      throw failure;
    }
  }
}
