package dev.erst.fingrind.executor.workflow;

import java.util.Objects;

/** Local workflow execution result before published-language projection. */
public record BookWorkflowExecutionResult(
    String planId, BookWorkflowExecutionStatus status, BookWorkflowExecutionJournal journal) {
  public BookWorkflowExecutionResult {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(journal, "journal");
  }
}
