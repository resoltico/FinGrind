package dev.erst.fingrind.executor.workflow;

import java.util.Objects;

/** Evaluates one workflow assertion after the preceding step effects are visible. */
public record BookWorkflowAssertionStep(BookWorkflowStepId stepId, BookWorkflowAssertion assertion)
    implements BookWorkflowStep {
  /** Validates the assertion step. */
  public BookWorkflowAssertionStep {
    Objects.requireNonNull(stepId, "stepId");
    Objects.requireNonNull(assertion, "assertion");
  }

  @Override
  public boolean mutatesBook() {
    return false;
  }
}
