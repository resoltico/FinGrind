package dev.erst.fingrind.executor.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable context for one plan-boundary failure that must produce a terminal journal entry. */
record BookWorkflowBoundaryFailureContext(
    BookWorkflowPlanId planId,
    Instant planStartedAt,
    List<BookWorkflowJournalEntry> entries,
    BookWorkflowBoundaryCheckpoint checkpoint,
    Instant checkpointStartedAt,
    @Nullable BookWorkflowStepId triggerStepId,
    @Nullable BookWorkflowJournalDescriptor triggerDescriptor) {
  BookWorkflowBoundaryFailureContext {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(planStartedAt, "planStartedAt");
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(checkpoint, "checkpoint");
    Objects.requireNonNull(checkpointStartedAt, "checkpointStartedAt");
  }

  static BookWorkflowBoundaryFailureContext begin(
      BookWorkflowPlanId planId, Instant planStartedAt, List<BookWorkflowJournalEntry> entries) {
    return new BookWorkflowBoundaryFailureContext(
        planId,
        planStartedAt,
        entries,
        BookWorkflowBoundaryCheckpoint.BEGIN,
        planStartedAt,
        null,
        null);
  }

  static BookWorkflowBoundaryFailureContext beforeStep(
      BookWorkflowPlanId planId,
      Instant planStartedAt,
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowBoundaryCheckpoint checkpoint,
      BookWorkflowStep step,
      Instant checkpointStartedAt) {
    Objects.requireNonNull(step, "step");
    return new BookWorkflowBoundaryFailureContext(
        planId,
        planStartedAt,
        entries,
        checkpoint,
        checkpointStartedAt,
        step.stepId(),
        new BookWorkflowJournalDescriptor.Step(step));
  }

  static BookWorkflowBoundaryFailureContext afterJournalEntry(
      BookWorkflowPlanId planId,
      Instant planStartedAt,
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowBoundaryCheckpoint checkpoint,
      BookWorkflowJournalEntry entry,
      Instant checkpointStartedAt) {
    Objects.requireNonNull(entry, "entry");
    return new BookWorkflowBoundaryFailureContext(
        planId,
        planStartedAt,
        entries,
        checkpoint,
        checkpointStartedAt,
        entry.stepId(),
        entry.descriptor());
  }
}
