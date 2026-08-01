package dev.erst.fingrind.executor.workflow;

import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.EXECUTED_AT;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.POSTING_ID;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.postEntryCommand;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.postingQuery;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.stepId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.PostingId;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies chain-backed provenance is restored across every relevant workflow journal shape. */
class BookWorkflowJournalAttestationHydratorTest {
  private static final PostingId PAGE_POSTING_ID =
      new PostingId("019f8e4b-771b-7b8b-8f7f-4ce8e1b2ea8c");
  private static final PostingId REPLAY_POSTING_ID =
      new PostingId("019f8e4b-771b-7b8b-8f7f-4ce8e1b2ea8d");
  private static final AttestationCommit AGGREGATE_COMMIT =
      new AttestationCommit(BigInteger.valueOf(42), "a".repeat(64));
  private static final AttestationCommit PAGE_COMMIT =
      new AttestationCommit(BigInteger.valueOf(7), "b".repeat(64));

  @Test
  void hydrate_reprojectsFreshAndStoredPostingQueriesWhileLeavingOtherJournalEntriesUntouched() {
    BookWorkflowStep.PostEntry freshPost =
        new BookWorkflowStep.PostEntry(stepId("fresh-post"), postEntryCommand("fresh-post"));
    BookWorkflowStep.PostEntry replayPost =
        new BookWorkflowStep.PostEntry(stepId("replay-post"), postEntryCommand("replay-post"));
    BookWorkflowStep.GetPosting getFreshPosting =
        new BookWorkflowStep.GetPosting(stepId("get-fresh"), POSTING_ID);
    BookWorkflowStep.ListPostings listPostings =
        new BookWorkflowStep.ListPostings(stepId("list-postings"), postingQuery());
    BookWorkflowStep.InspectBook inspectBook =
        new BookWorkflowStep.InspectBook(stepId("inspect-book"));
    BookWorkflowJournalEntry.Rejected rejected =
        rejected(new BookWorkflowStep.InspectBook(stepId("rejected")));
    BookWorkflowJournalEntry.Succeeded boundary = boundarySucceeded();

    List<BookWorkflowJournalEntry> entries =
        List.of(
            succeeded(freshPost, postFacts(POSTING_ID, false)),
            succeeded(replayPost, postFacts(REPLAY_POSTING_ID, true)),
            succeeded(getFreshPosting, queryFacts(POSTING_ID)),
            succeeded(
                listPostings,
                List.of(
                    BookWorkflowFact.group("posting", queryFacts(PAGE_POSTING_ID)),
                    BookWorkflowFact.group(
                        "pagination", List.of(BookWorkflowFact.text("limit", "10"))))),
            succeeded(inspectBook, List.of(BookWorkflowFact.text("state", "initialized"))),
            boundary,
            rejected);

    List<BookWorkflowJournalEntry> hydrated =
        BookWorkflowJournalAttestationHydrator.hydrate(
            entries, postingIds -> commitmentsFor(postingIds), AGGREGATE_COMMIT);

    assertEquals(AGGREGATE_COMMIT, commitmentFrom(hydrated.get(2).facts()));
    BookWorkflowFact.Group postingPageGroup =
        (BookWorkflowFact.Group) hydrated.get(3).facts().getFirst();
    assertEquals(PAGE_COMMIT, commitmentFrom(postingPageGroup.facts()));
    assertSame(entries.get(4), hydrated.get(4));
    assertSame(boundary, hydrated.get(5));
    assertSame(rejected, hydrated.get(6));
  }

  @Test
  void hydrate_returnsTheCheckedJournalWhenNoPostingQueryWasExecuted() {
    BookWorkflowJournalEntry.Succeeded inspection =
        succeeded(
            new BookWorkflowStep.InspectBook(stepId("inspect")),
            List.of(BookWorkflowFact.text("state", "initialized")));
    List<BookWorkflowJournalEntry> entries = List.of(inspection, boundarySucceeded());

    List<BookWorkflowJournalEntry> hydrated =
        BookWorkflowJournalAttestationHydrator.hydrate(
            entries,
            ignored -> {
              throw new AssertionError(
                  "Non-query workflow entries must not request attestation facts.");
            },
            AGGREGATE_COMMIT);

    assertEquals(entries, hydrated);
  }

  private static Map<PostingId, AttestationCommit> commitmentsFor(Set<PostingId> postingIds) {
    assertTrue(postingIds.contains(POSTING_ID));
    assertTrue(postingIds.contains(PAGE_POSTING_ID));
    return Map.of(POSTING_ID, AGGREGATE_COMMIT, PAGE_POSTING_ID, PAGE_COMMIT);
  }

  private static List<BookWorkflowFact> postFacts(PostingId postingId, boolean idempotentReplay) {
    return List.of(
        BookWorkflowFact.text("postingId", postingId.value()),
        BookWorkflowFact.text("recordedAt", EXECUTED_AT.toString()),
        BookWorkflowFact.flag("idempotentReplay", idempotentReplay));
  }

  private static List<BookWorkflowFact> queryFacts(PostingId postingId) {
    return List.of(
        BookWorkflowFact.text("postingId", postingId.value()),
        BookWorkflowFact.text("recordedAt", EXECUTED_AT.toString()));
  }

  private static BookWorkflowJournalEntry.Succeeded succeeded(
      BookWorkflowStep step, List<BookWorkflowFact> facts) {
    return new BookWorkflowJournalEntry.Succeeded(
        step.stepId(),
        new BookWorkflowJournalDescriptor.Step(step),
        EXECUTED_AT,
        EXECUTED_AT,
        facts);
  }

  private static BookWorkflowJournalEntry.Succeeded boundarySucceeded() {
    return new BookWorkflowJournalEntry.Succeeded(
        new BookWorkflowStepId("@boundary"),
        new BookWorkflowJournalDescriptor.Boundary(BookWorkflowBoundaryCheckpoint.COMMIT),
        EXECUTED_AT,
        EXECUTED_AT,
        List.of());
  }

  private static BookWorkflowJournalEntry.Rejected rejected(BookWorkflowStep step) {
    return new BookWorkflowJournalEntry.Rejected(
        step.stepId(),
        new BookWorkflowJournalDescriptor.Step(step),
        EXECUTED_AT,
        EXECUTED_AT,
        List.of(),
        new BookWorkflowFailure("rejected", "The query was rejected.", List.of()));
  }

  private static AttestationCommit commitmentFrom(List<BookWorkflowFact> facts) {
    BookWorkflowFact.Group group =
        (BookWorkflowFact.Group)
            facts.stream()
                .filter(fact -> fact instanceof BookWorkflowFact.Group)
                .filter(fact -> "attestationCommit".equals(fact.name()))
                .findFirst()
                .orElseThrow();
    String operationOrder = ((BookWorkflowFact.Text) group.facts().getFirst()).value();
    String operationHead = ((BookWorkflowFact.Text) group.facts().get(1)).value();
    return new AttestationCommit(new BigInteger(operationOrder), operationHead);
  }
}
