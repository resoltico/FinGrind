package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.core.InteractionLimits;
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
    if (steps.size() > InteractionLimits.LEDGER_PLAN_STEP_MAX) {
      throw new IllegalArgumentException(
          "Workflow plan must not contain more than "
              + InteractionLimits.LEDGER_PLAN_STEP_MAX
              + " steps.");
    }
    if (steps.stream().map(BookWorkflowStep::stepId).distinct().count() != steps.size()) {
      throw new IllegalArgumentException("Workflow plan stepId values must be unique.");
    }
    if (steps.stream().skip(1).anyMatch(BookWorkflowStep.OpenBook.class::isInstance)) {
      throw new IllegalArgumentException(
          "Workflow plan open book step must be first when present.");
    }
  }

  /** Returns whether this plan begins by initializing the selected book. */
  public boolean beginsWithOpenBook() {
    return steps.getFirst() instanceof BookWorkflowStep.OpenBook;
  }
}
