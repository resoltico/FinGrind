package dev.erst.fingrind.executor.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.executor.workflow.BookWorkflowJournalEntry.Succeeded;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Coverage test for private workflow execution invariants. */
class BookWorkflowExecutionServiceInvariantTest {
  private static final MethodHandle STEP_EXECUTION_STATE_CONSTRUCTOR =
      stepExecutionStateConstructor();

  @Test
  void stepExecutionStateRejectsMissingTerminalAndPendingOutcomes() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> newStepExecutionState(null, null));

    assertEquals(
        "Exactly one of terminalResult or pendingSuccessfulStep must be present.",
        exception.getMessage());
  }

  @Test
  void executionResult_rejectsCommitmentWhenThePlanDidNotSucceed() {
    BookWorkflowExecutionJournal rejectedJournal =
        new BookWorkflowExecutionJournal(
            Instant.EPOCH,
            Instant.EPOCH,
            List.of(
                new BookWorkflowJournalEntry.Rejected(
                    new BookWorkflowStepId("reject"),
                    new BookWorkflowJournalDescriptor.Boundary(
                        BookWorkflowBoundaryCheckpoint.COMMIT),
                    Instant.EPOCH,
                    Instant.EPOCH,
                    List.of(),
                    new BookWorkflowFailure("commit-failed", "Commit failed.", List.of()))));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookWorkflowExecutionResult(
                    new BookWorkflowPlanId("plan-1"),
                    BookWorkflowExecutionStatus.REJECTED,
                    rejectedJournal,
                    new AttestationCommit(BigInteger.ONE, "a".repeat(64))));

    assertEquals(
        "Only a successfully committed plan may report an attestation commitment.",
        exception.getMessage());
  }

  private static MethodHandle stepExecutionStateConstructor() {
    try {
      Class<?> stateClass = Class.forName(BookWorkflowStepExecutionState.class.getName());
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(stateClass, MethodHandles.lookup());
      return lookup.findConstructor(
          stateClass,
          MethodType.methodType(void.class, BookWorkflowExecutionResult.class, Succeeded.class));
    } catch (ReflectiveOperationException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static Object newStepExecutionState(
      @Nullable BookWorkflowExecutionResult terminalResult,
      @Nullable Succeeded pendingSuccessfulStep) {
    try {
      return STEP_EXECUTION_STATE_CONSTRUCTOR.invoke(terminalResult, pendingSuccessfulStep);
    } catch (RuntimeException | Error exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new AssertionError(throwable);
    }
  }
}
