package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import java.util.List;
import java.util.Objects;

/** Internal workflow plan translated from the public execute-plan schema. */
public record BookWorkflowPlan(String planId, List<BookWorkflowStep> steps) {
  /** Validates one translated workflow plan. */
  public BookWorkflowPlan {
    Objects.requireNonNull(planId, "planId");
    if (planId.isBlank()) {
      throw new IllegalArgumentException("Workflow planId must not be blank.");
    }
    steps = steps == null ? List.of() : List.copyOf(steps);
    if (steps.isEmpty()) {
      throw new IllegalArgumentException("Workflow plan must contain at least one step.");
    }
    if (steps.size() > ProtocolLimits.LEDGER_PLAN_STEP_MAX) {
      throw new IllegalArgumentException(
          "Workflow plan must not contain more than "
              + ProtocolLimits.LEDGER_PLAN_STEP_MAX
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
