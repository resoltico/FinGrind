package dev.erst.fingrind.executor.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.workflow.BookWorkflowJournalEntry.Succeeded;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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

  private static MethodHandle stepExecutionStateConstructor() {
    try {
      Class<?> stateClass =
          Class.forName(BookWorkflowExecutionService.class.getName() + "$StepExecutionState");
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
