package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.workflow.BookWorkflowAssertion;
import dev.erst.fingrind.executor.workflow.BookWorkflowBoundaryPhase;
import dev.erst.fingrind.executor.workflow.BookWorkflowExecutionJournal;
import dev.erst.fingrind.executor.workflow.BookWorkflowExecutionStatus;
import dev.erst.fingrind.executor.workflow.BookWorkflowFact;
import dev.erst.fingrind.executor.workflow.BookWorkflowFailure;
import dev.erst.fingrind.executor.workflow.BookWorkflowJournalDescriptor;
import dev.erst.fingrind.executor.workflow.BookWorkflowJournalEntry;
import dev.erst.fingrind.executor.workflow.BookWorkflowStep;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for the local workflow journal and failure model. */
class BookWorkflowJournalModelTest {
  private static final Instant STARTED_AT = Instant.parse("2026-05-06T10:15:30Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-05-06T10:15:45Z");

  @Test
  void journalEntries_exposeOptionalAndRequiredFailurePayloadsByVariant() {
    var succeeded = succeeded("inspect");
    var rejected = rejected("reject");
    var assertionFailed = assertionFailed("assert-balance");

    assertEquals(Optional.empty(), succeeded.optionalFailure());
    assertEquals(rejected.failure(), rejected.optionalFailure().orElseThrow());
    assertEquals(assertionFailed.failure(), assertionFailed.optionalFailure().orElseThrow());
    assertEquals(rejected.failure(), rejected.requiredFailure());
    assertEquals(assertionFailed.failure(), assertionFailed.requiredFailure());
    assertEquals(
        "Workflow journal entry does not carry a failure.",
        assertThrows(IllegalStateException.class, succeeded::requiredFailure).getMessage());
  }

  @Test
  void journalEntries_rejectInvalidDescriptorsAndTiming() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookWorkflowJournalEntry.AssertionFailed(
                "assert-balance",
                inspectDescriptor("inspect"),
                STARTED_AT,
                FINISHED_AT,
                List.of(),
                failure("assertion-failed")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookWorkflowJournalEntry.AssertionFailed(
                "assert-balance",
                new BookWorkflowJournalDescriptor.Boundary(BookWorkflowBoundaryPhase.COMMIT),
                STARTED_AT,
                FINISHED_AT,
                List.of(),
                failure("assertion-failed")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookWorkflowJournalEntry.Rejected(
                "   ",
                inspectDescriptor("inspect"),
                STARTED_AT,
                FINISHED_AT,
                List.of(),
                failure("rejected")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookWorkflowJournalEntry.Rejected(
                "reject",
                inspectDescriptor("inspect"),
                FINISHED_AT,
                STARTED_AT,
                List.of(),
                failure("rejected")));
  }

  @Test
  @SuppressWarnings("NullAway")
  void executionJournal_derivesTerminalStatusAndRejectsInvalidSequences() {
    var succeeded = succeeded("inspect");
    var rejected = rejected("reject");
    var assertionFailed = assertionFailed("assert-balance");
    BookWorkflowExecutionJournal succeededJournal =
        new BookWorkflowExecutionJournal(
            STARTED_AT, FINISHED_AT, List.of(succeeded, succeeded("list")));
    BookWorkflowExecutionJournal rejectedJournal =
        new BookWorkflowExecutionJournal(STARTED_AT, FINISHED_AT, List.of(succeeded, rejected));
    BookWorkflowExecutionJournal assertionFailedJournal =
        new BookWorkflowExecutionJournal(
            STARTED_AT, FINISHED_AT, List.of(succeeded, assertionFailed));

    assertEquals(succeededJournal.entries().getLast(), succeededJournal.terminalEntry());
    assertEquals(BookWorkflowExecutionStatus.SUCCEEDED, succeededJournal.status());
    assertEquals(
        "Succeeded workflow journals do not have a failed entry.",
        assertThrows(IllegalStateException.class, succeededJournal::requiredFailedEntry)
            .getMessage());
    assertEquals(BookWorkflowExecutionStatus.REJECTED, rejectedJournal.status());
    assertEquals(rejected, rejectedJournal.requiredFailedEntry());
    assertEquals(BookWorkflowExecutionStatus.ASSERTION_FAILED, assertionFailedJournal.status());
    assertEquals(assertionFailed, assertionFailedJournal.requiredFailedEntry());
    assertEquals(
        "entries",
        assertThrows(
                NullPointerException.class,
                () -> new BookWorkflowExecutionJournal(STARTED_AT, FINISHED_AT, nullOf()))
            .getMessage());
    assertEquals(
        "Workflow execution journal must contain at least one entry.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new BookWorkflowExecutionJournal(STARTED_AT, FINISHED_AT, List.of()))
            .getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () -> new BookWorkflowExecutionJournal(FINISHED_AT, STARTED_AT, List.of(succeeded)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookWorkflowExecutionJournal(
                STARTED_AT, FINISHED_AT, List.of(rejected("reject-1"), succeeded("inspect-2"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookWorkflowExecutionJournal(
                STARTED_AT,
                FINISHED_AT,
                List.of(assertionFailed("assert-1"), rejected("reject-2"))));
  }

  @Test
  void workflowFailure_rejectsNullFactsAndCopiesProvidedFacts() {
    List<BookWorkflowFact> mutableFacts =
        new ArrayList<>(List.of(BookWorkflowFact.text("code", "value")));
    BookWorkflowFailure copiedFacts = new BookWorkflowFailure("code", "message", mutableFacts);

    mutableFacts.clear();

    assertEquals(List.of(BookWorkflowFact.text("code", "value")), copiedFacts.facts());
    assertEquals(
        "facts",
        assertThrows(
                NullPointerException.class,
                () -> new BookWorkflowFailure("code", "message", nullOf()))
            .getMessage());
  }

  private static BookWorkflowJournalEntry.Succeeded succeeded(String stepId) {
    return new BookWorkflowJournalEntry.Succeeded(
        stepId,
        inspectDescriptor(stepId),
        STARTED_AT,
        FINISHED_AT,
        List.of(BookWorkflowFact.flag("ok", true)));
  }

  private static BookWorkflowJournalEntry.Rejected rejected(String stepId) {
    return new BookWorkflowJournalEntry.Rejected(
        stepId, inspectDescriptor(stepId), STARTED_AT, FINISHED_AT, List.of(), failure("rejected"));
  }

  private static BookWorkflowJournalEntry.AssertionFailed assertionFailed(String stepId) {
    return new BookWorkflowJournalEntry.AssertionFailed(
        stepId,
        assertionDescriptor(stepId),
        STARTED_AT,
        FINISHED_AT,
        List.of(),
        failure("assertion-failed"));
  }

  private static BookWorkflowFailure failure(String code) {
    return new BookWorkflowFailure(
        code, "failure message", List.of(BookWorkflowFact.text("reason", "example")));
  }

  private static BookWorkflowJournalDescriptor.Step inspectDescriptor(String stepId) {
    return new BookWorkflowJournalDescriptor.Step(new BookWorkflowStep.InspectBook(stepId));
  }

  private static BookWorkflowJournalDescriptor.Step assertionDescriptor(String stepId) {
    return new BookWorkflowJournalDescriptor.Step(
        new BookWorkflowStep.Assert(
            stepId, new BookWorkflowAssertion.AccountDeclared(CASH_ACCOUNT.accountCode())));
  }
}
