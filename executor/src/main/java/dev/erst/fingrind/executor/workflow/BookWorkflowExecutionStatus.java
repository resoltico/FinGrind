package dev.erst.fingrind.executor.workflow;

/** Internal workflow execution status derived from the terminal workflow journal entry. */
public enum BookWorkflowExecutionStatus {
  SUCCEEDED,
  REJECTED,
  ASSERTION_FAILED
}
