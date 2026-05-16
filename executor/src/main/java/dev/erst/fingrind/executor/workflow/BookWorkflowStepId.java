package dev.erst.fingrind.executor.workflow;

import java.util.Objects;

/** Stable internal identifier for one translated workflow step. */
public record BookWorkflowStepId(String value) {
  /** Validates one workflow step identifier at the executor boundary. */
  public BookWorkflowStepId {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Workflow step id must not be blank.");
    }
  }
}
