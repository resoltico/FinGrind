package dev.erst.fingrind.executor.workflow;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

  /** Returns whether this local plan contains a step that durably changes the protected book. */
  boolean containsBookMutation() {
    return firstBookMutationStep().isPresent();
  }

  /** Returns the first book-mutating step, if this plan contains one. */
  Optional<BookWorkflowStep> firstBookMutationStep() {
    return steps.stream().filter(BookWorkflowStep::mutatesBook).findFirst();
  }
}
