package dev.erst.fingrind.executor.workflow;

import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.POSTING_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies exact attestation-commitment facts projected into workflow posting results. */
class BookWorkflowPostingAttestationFactProjectionTest {
  private static final AttestationCommit AGGREGATE_COMMIT = commit(42, 'a');
  private static final AttestationCommit EXISTING_COMMIT = commit(7, 'b');
  private static final AttestationCommit DIFFERENT_COMMIT = commit(8, 'c');

  @Test
  void hydrate_replacesOneExistingCommitmentAtRecordedAtAndPreservesOtherFacts() {
    List<BookWorkflowFact> facts =
        List.of(
            BookWorkflowFact.text("postingId", POSTING_ID.value()),
            BookWorkflowFact.group("request", List.of(BookWorkflowFact.text("source", "agent"))),
            BookWorkflowFact.text("recordedAt", "2026-07-24T10:15:30Z"),
            commitmentFact(EXISTING_COMMIT),
            BookWorkflowFact.flag("idempotentReplay", false));

    List<BookWorkflowFact> hydrated =
        BookWorkflowPostingAttestationFactProjection.hydrate(
            facts, Map.of(POSTING_ID, EXISTING_COMMIT), Set.of(), AGGREGATE_COMMIT);

    assertEquals(
        List.of(
            BookWorkflowFact.text("postingId", POSTING_ID.value()),
            BookWorkflowFact.group("request", List.of(BookWorkflowFact.text("source", "agent"))),
            BookWorkflowFact.text("recordedAt", "2026-07-24T10:15:30Z"),
            commitmentFact(EXISTING_COMMIT),
            BookWorkflowFact.flag("idempotentReplay", false)),
        hydrated);
  }

  @Test
  void hydrate_addsTheAggregateCommitOnlyForFreshPostingsAndOmitsAbsentCommitments() {
    List<BookWorkflowFact> fresh =
        BookWorkflowPostingAttestationFactProjection.hydrate(
            postingFacts(),
            Map.of(POSTING_ID, AGGREGATE_COMMIT),
            Set.of(POSTING_ID),
            AGGREGATE_COMMIT);
    List<BookWorkflowFact> absent =
        BookWorkflowPostingAttestationFactProjection.hydrate(
            postingFacts(), Map.of(), Set.of(), AGGREGATE_COMMIT);

    assertTrue(fresh.contains(commitmentFact(AGGREGATE_COMMIT)));
    assertFalse(absent.stream().anyMatch(fact -> "attestationCommit".equals(fact.name())));
  }

  @Test
  void hydrate_rejectsCommitmentInvariantViolationsInsteadOfSilentlyRewritingEvidence() {
    assertProjectionFailure(
        "newly committed plan posting",
        () ->
            BookWorkflowPostingAttestationFactProjection.hydrate(
                postingFacts(), Map.of(), Set.of(POSTING_ID), AGGREGATE_COMMIT));
    assertProjectionFailure(
        "newly committed plan posting",
        () ->
            BookWorkflowPostingAttestationFactProjection.hydrate(
                postingFacts(),
                Map.of(POSTING_ID, DIFFERENT_COMMIT),
                Set.of(POSTING_ID),
                AGGREGATE_COMMIT));
    assertProjectionFailure(
        "must remain unchanged",
        () ->
            BookWorkflowPostingAttestationFactProjection.hydrate(
                factsWithCommitment(EXISTING_COMMIT),
                Map.of(POSTING_ID, DIFFERENT_COMMIT),
                Set.of(),
                AGGREGATE_COMMIT));
    assertProjectionFailure(
        "at most one attestationCommit",
        () ->
            BookWorkflowPostingAttestationFactProjection.hydrate(
                List.of(
                    BookWorkflowFact.text("postingId", POSTING_ID.value()),
                    BookWorkflowFact.text("recordedAt", "2026-07-24T10:15:30Z"),
                    commitmentFact(EXISTING_COMMIT),
                    commitmentFact(EXISTING_COMMIT)),
                Map.of(POSTING_ID, EXISTING_COMMIT),
                Set.of(),
                AGGREGATE_COMMIT));
    assertProjectionFailure(
        "must contain recordedAt",
        () ->
            BookWorkflowPostingAttestationFactProjection.hydrate(
                List.of(BookWorkflowFact.text("postingId", POSTING_ID.value())),
                Map.of(),
                Set.of(),
                AGGREGATE_COMMIT));
  }

  @Test
  void idempotentReplayAndPostingIdFactsMustBeSingularAndPresent() {
    assertTrue(
        BookWorkflowPostingAttestationFactProjection.idempotentReplayFrom(
            List.of(BookWorkflowFact.flag("idempotentReplay", true))));
    assertFalse(
        BookWorkflowPostingAttestationFactProjection.idempotentReplayFrom(
            List.of(
                BookWorkflowFact.text("other", "value"),
                BookWorkflowFact.flag("other", false),
                BookWorkflowFact.flag("idempotentReplay", false))));
    assertProjectionFailure(
        "one idempotentReplay flag",
        () -> BookWorkflowPostingAttestationFactProjection.idempotentReplayFrom(List.of()));
    assertProjectionFailure(
        "exactly one idempotentReplay flag",
        () ->
            BookWorkflowPostingAttestationFactProjection.idempotentReplayFrom(
                List.of(
                    BookWorkflowFact.flag("idempotentReplay", false),
                    BookWorkflowFact.flag("idempotentReplay", true))));

    assertEquals(
        POSTING_ID,
        BookWorkflowPostingAttestationFactProjection.postingIdFrom(
            List.of(BookWorkflowFact.text("postingId", POSTING_ID.value()))));
    assertProjectionFailure(
        "contain one postingId",
        () ->
            BookWorkflowPostingAttestationFactProjection.postingIdFrom(
                List.of(BookWorkflowFact.text("other", "value"))));
    assertProjectionFailure(
        "exactly one postingId",
        () ->
            BookWorkflowPostingAttestationFactProjection.postingIdFrom(
                List.of(
                    BookWorkflowFact.text("postingId", POSTING_ID.value()),
                    BookWorkflowFact.text("postingId", POSTING_ID.value()))));
  }

  @Test
  void hydrate_rejectsMalformedPersistedCommitmentFacts() {
    assertMalformedCommitment(
        List.of(BookWorkflowFact.flag("operationOrder", true)), "must use text-valued");
    assertMalformedCommitment(List.of(BookWorkflowFact.text("unknown", "value")), "unknown field");
    assertMalformedCommitment(
        List.of(
            BookWorkflowFact.text("operationOrder", "1"),
            BookWorkflowFact.text("operationOrder", "2"),
            BookWorkflowFact.text("operationHead", "a".repeat(64))),
        "must contain one operationOrder");
    assertMalformedCommitment(
        List.of(BookWorkflowFact.text("operationHead", "a".repeat(64))),
        "must contain operationOrder and operationHead");
    assertMalformedCommitment(
        List.of(BookWorkflowFact.text("operationOrder", "1")),
        "must contain operationOrder and operationHead");
    assertMalformedCommitment(
        List.of(
            BookWorkflowFact.text("operationOrder", "not-a-number"),
            BookWorkflowFact.text("operationHead", "a".repeat(64))),
        "must encode a valid commitment");
  }

  private static void assertMalformedCommitment(
      List<BookWorkflowFact> commitmentFacts, String expectedMessagePart) {
    assertProjectionFailure(
        expectedMessagePart,
        () ->
            BookWorkflowPostingAttestationFactProjection.hydrate(
                List.of(
                    BookWorkflowFact.text("postingId", POSTING_ID.value()),
                    BookWorkflowFact.text("recordedAt", "2026-07-24T10:15:30Z"),
                    BookWorkflowFact.group("attestationCommit", commitmentFacts)),
                Map.of(),
                Set.of(),
                AGGREGATE_COMMIT));
  }

  private static List<BookWorkflowFact> postingFacts() {
    return List.of(
        BookWorkflowFact.text("postingId", POSTING_ID.value()),
        BookWorkflowFact.group("request", List.of(BookWorkflowFact.text("source", "agent"))),
        BookWorkflowFact.text("recordedAt", "2026-07-24T10:15:30Z"));
  }

  private static List<BookWorkflowFact> factsWithCommitment(AttestationCommit commitment) {
    return List.of(
        BookWorkflowFact.text("postingId", POSTING_ID.value()),
        BookWorkflowFact.text("recordedAt", "2026-07-24T10:15:30Z"),
        commitmentFact(commitment));
  }

  private static BookWorkflowFact.Group commitmentFact(AttestationCommit commitment) {
    return BookWorkflowFact.group(
        "attestationCommit",
        List.of(
            BookWorkflowFact.text("operationOrder", commitment.operationOrder().toString()),
            BookWorkflowFact.text("operationHead", commitment.operationHeadHex())));
  }

  private static AttestationCommit commit(long operationOrder, char headCharacter) {
    return new AttestationCommit(
        BigInteger.valueOf(operationOrder), String.valueOf(headCharacter).repeat(64));
  }

  private static void assertProjectionFailure(String expectedMessagePart, Runnable action) {
    IllegalStateException exception = assertThrows(IllegalStateException.class, action::run);

    assertTrue(Objects.requireNonNull(exception.getMessage()).contains(expectedMessagePart));
  }
}
