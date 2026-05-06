package dev.erst.fingrind.executor.workflow;

import java.util.Objects;

/** Internal journal descriptor for one executed workflow step or plan-boundary phase. */
public sealed interface BookWorkflowJournalDescriptor
    permits BookWorkflowJournalDescriptor.Step, BookWorkflowJournalDescriptor.Boundary {
  /** Journal descriptor for one normal plan step. */
  record Step(BookWorkflowStep step) implements BookWorkflowJournalDescriptor {
    public Step {
      Objects.requireNonNull(step, "step");
    }
  }

  /** Journal descriptor for one begin/check/commit/rollback boundary phase. */
  record Boundary(BookWorkflowBoundaryPhase phase) implements BookWorkflowJournalDescriptor {
    public Boundary {
      Objects.requireNonNull(phase, "phase");
    }
  }
}
