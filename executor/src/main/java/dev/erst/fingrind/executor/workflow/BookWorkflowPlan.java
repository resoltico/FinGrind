package dev.erst.fingrind.executor.workflow;

import java.util.List;
import java.util.Objects;

/** Internal workflow plan translated from the public execute-plan schema. */
public record BookWorkflowPlan(BookWorkflowPlanId planId, List<BookWorkflowStep> steps) {
  /** Validates one translated workflow plan. */
  public BookWorkflowPlan {
    Objects.requireNonNull(planId, "planId");
    steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    if (steps.isEmpty()) {
      throw new IllegalArgumentException("Workflow plan must contain at least one step.");
    }
  }

  /** Returns whether this plan begins by ensuring the selected book exists. */
  public boolean beginsWithEnsureBook() {
    return steps.getFirst() instanceof BookWorkflowStep.EnsureBook;
  }
}
