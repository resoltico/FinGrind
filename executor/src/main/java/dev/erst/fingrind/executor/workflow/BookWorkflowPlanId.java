package dev.erst.fingrind.executor.workflow;

import java.util.Objects;

/** Stable internal identifier for one translated workflow plan. */
public record BookWorkflowPlanId(String value) {
  /** Validates one workflow plan identifier at the executor boundary. */
  public BookWorkflowPlanId {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Workflow plan id must not be blank.");
    }
  }
}
