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
    BookWorkflowBoundaryPhase phase,
    Instant phaseStartedAt,
    @Nullable BookWorkflowStepId triggerStepId,
    @Nullable BookWorkflowJournalDescriptor triggerDescriptor) {
  BookWorkflowBoundaryFailureContext {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(planStartedAt, "planStartedAt");
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(phase, "phase");
    Objects.requireNonNull(phaseStartedAt, "phaseStartedAt");
  }

  static BookWorkflowBoundaryFailureContext begin(
      BookWorkflowPlanId planId, Instant planStartedAt, List<BookWorkflowJournalEntry> entries) {
    return new BookWorkflowBoundaryFailureContext(
        planId, planStartedAt, entries, BookWorkflowBoundaryPhase.BEGIN, planStartedAt, null, null);
  }

  static BookWorkflowBoundaryFailureContext beforeStep(
      BookWorkflowPlanId planId,
      Instant planStartedAt,
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowBoundaryPhase phase,
      BookWorkflowStep step,
      Instant phaseStartedAt) {
    Objects.requireNonNull(step, "step");
    return new BookWorkflowBoundaryFailureContext(
        planId,
        planStartedAt,
        entries,
        phase,
        phaseStartedAt,
        step.stepId(),
        new BookWorkflowJournalDescriptor.Step(step));
  }

  static BookWorkflowBoundaryFailureContext afterJournalEntry(
      BookWorkflowPlanId planId,
      Instant planStartedAt,
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowBoundaryPhase phase,
      BookWorkflowJournalEntry entry,
      Instant phaseStartedAt) {
    Objects.requireNonNull(entry, "entry");
    return new BookWorkflowBoundaryFailureContext(
        planId, planStartedAt, entries, phase, phaseStartedAt, entry.stepId(), entry.descriptor());
  }
}
