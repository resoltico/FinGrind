package dev.erst.fingrind.executor.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Focused coverage for shared rollback recovery across mutable and read-only workflows. */
class BookWorkflowFailureRecoveryTest {
  private static final Instant EXECUTED_AT = Instant.parse("2026-07-24T09:30:00Z");
  private static final Clock CLOCK = Clock.fixed(EXECUTED_AT, ZoneOffset.UTC);

  @Test
  void failedResultWithRollback_retainsTheOriginalFailureAfterOneSuccessfulRollback() {
    AtomicInteger rollbackCalls = new AtomicInteger();
    BookWorkflowFailureRecovery recovery = recovery(rollbackCalls::incrementAndGet);
    BookWorkflowJournalEntry.Rejected rejected = rejected("account-state-violations");
    List<BookWorkflowJournalEntry> entries = new ArrayList<>();

    BookWorkflowExecutionResult result =
        recovery.failedResultWithRollback(
            new BookWorkflowPlanId("plan-1"), EXECUTED_AT, entries, rejected);

    assertEquals(1, rollbackCalls.get());
    assertEquals(BookWorkflowExecutionStatus.REJECTED, result.status());
    assertEquals(List.of(rejected), entries);
    assertSame(rejected, result.journal().terminalEntry());
  }

  @Test
  void failedResultWithRollback_projectsRollbackFailureAndRetainsThePriorFailure() {
    BookWorkflowFailureRecovery recovery =
        recovery(
            () -> {
              throw new IllegalStateException("rollback boom");
            });
    BookWorkflowJournalEntry.Rejected rejected = rejected("account-state-violations");
    List<BookWorkflowJournalEntry> entries = new ArrayList<>();

    BookWorkflowExecutionResult result =
        recovery.failedResultWithRollback(
            new BookWorkflowPlanId("plan-1"), EXECUTED_AT, entries, rejected);

    BookWorkflowJournalEntry.Failed terminalFailure = result.journal().requiredFailedEntry();
    assertEquals(BookWorkflowExecutionStatus.REJECTED, result.status());
    assertEquals("@plan-boundary:rollback", terminalFailure.stepId().value());
    assertEquals("unexpected-plan-failure", terminalFailure.requiredFailure().code());
    assertTrue(
        terminalFailure.requiredFailure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && "priorFailure".equals(group.name())
                        && group
                            .facts()
                            .contains(BookWorkflowFact.text("code", "account-state-violations"))));
  }

  private static BookWorkflowFailureRecovery recovery(Runnable rollback) {
    return new BookWorkflowFailureRecovery(
        CLOCK, new BookWorkflowExecutionResultFactory(CLOCK), rollback);
  }

  private static BookWorkflowJournalEntry.Rejected rejected(String code) {
    return new BookWorkflowJournalEntry.Rejected(
        new BookWorkflowStepId("post"),
        new BookWorkflowJournalDescriptor.Boundary(BookWorkflowBoundaryCheckpoint.COMMIT),
        EXECUTED_AT,
        EXECUTED_AT,
        List.of(),
        new BookWorkflowFailure(code, "Rejected message.", List.of()));
  }
}
