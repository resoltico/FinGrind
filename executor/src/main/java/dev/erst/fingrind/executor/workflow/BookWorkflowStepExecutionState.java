package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.executor.workflow.BookWorkflowJournalEntry.Succeeded;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable outcome of running the workflow steps before the final commit boundary. */
record BookWorkflowStepExecutionState(
    @Nullable BookWorkflowExecutionResult terminalResult,
    @Nullable Succeeded pendingSuccessfulStep) {
  BookWorkflowStepExecutionState {
    if ((terminalResult == null) == (pendingSuccessfulStep == null)) {
      throw new IllegalArgumentException(
          "Exactly one of terminalResult or pendingSuccessfulStep must be present.");
    }
  }

  static BookWorkflowStepExecutionState terminal(BookWorkflowExecutionResult terminalResult) {
    return new BookWorkflowStepExecutionState(
        Objects.requireNonNull(terminalResult, "terminalResult"), null);
  }

  static BookWorkflowStepExecutionState pending(Succeeded successfulStep) {
    return new BookWorkflowStepExecutionState(
        null, Objects.requireNonNull(successfulStep, "successfulStep"));
  }
}
