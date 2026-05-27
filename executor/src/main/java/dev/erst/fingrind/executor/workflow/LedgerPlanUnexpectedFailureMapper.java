package dev.erst.fingrind.executor.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps unexpected plan/runtime failures into stable workflow journal entries. */
final class LedgerPlanUnexpectedFailureMapper {
  private LedgerPlanUnexpectedFailureMapper() {}

  static BookWorkflowJournalEntry.Rejected unexpectedExecutionFailure(
      BookWorkflowStep step, Instant startedAt, Instant finishedAt, RuntimeException failure) {
    List<BookWorkflowFact> facts =
        List.of(BookWorkflowFact.text("exceptionType", failure.getClass().getName()));
    return new BookWorkflowJournalEntry.Rejected(
        step.stepId(),
        new BookWorkflowJournalDescriptor.Step(step),
        startedAt,
        finishedAt,
        facts,
        new BookWorkflowFailure(
            "unexpected-step-failure", unexpectedExecutionFailureMessage(step, failure), facts));
  }

  static BookWorkflowJournalEntry.Rejected unexpectedPlanFailure(
      BookWorkflowBoundaryPhase phase,
      Instant startedAt,
      Instant finishedAt,
      @Nullable BookWorkflowStepId triggerStepId,
      @Nullable BookWorkflowJournalDescriptor triggerDescriptor,
      RuntimeException failure,
      @Nullable RuntimeException cleanupFailure,
      @Nullable BookWorkflowFailure priorFailure) {
    List<BookWorkflowFact> failureFacts = new ArrayList<>();
    failureFacts.add(BookWorkflowFact.text("phase", phase.wireValue()));
    failureFacts.add(BookWorkflowFact.text("exceptionType", failure.getClass().getName()));
    if (triggerStepId != null) {
      failureFacts.add(BookWorkflowFact.text("triggerStepId", triggerStepId.value()));
    }
    if (triggerDescriptor != null) {
      appendTriggerDescriptorFacts(failureFacts, triggerDescriptor);
    }
    if (cleanupFailure != null) {
      failureFacts.add(
          BookWorkflowFact.group(
              "cleanupFailure",
              List.of(
                  BookWorkflowFact.text("exceptionType", cleanupFailure.getClass().getName()))));
    }
    if (priorFailure != null) {
      failureFacts.add(
          BookWorkflowFact.group(
              "priorFailure",
              List.of(
                  BookWorkflowFact.text("code", priorFailure.code()),
                  BookWorkflowFact.text("message", priorFailure.message()))));
    }
    return new BookWorkflowJournalEntry.Rejected(
        boundaryStepId(phase),
        new BookWorkflowJournalDescriptor.Boundary(phase),
        startedAt,
        finishedAt,
        List.of(),
        new BookWorkflowFailure(
            "unexpected-plan-failure",
            unexpectedPlanFailureMessage(phase, triggerStepId, failure),
            failureFacts));
  }

  private static String unexpectedExecutionFailureMessage(
      BookWorkflowStep step, RuntimeException failure) {
    String detail = String.valueOf(failure.getMessage()).strip();
    if (detail.isEmpty() || "null".equals(detail)) {
      return "Ledger plan execution failed unexpectedly during step '%s'."
          .formatted(step.stepId().value());
    }
    return "Ledger plan execution failed unexpectedly during step '%s': %s"
        .formatted(step.stepId().value(), detail);
  }

  private static String unexpectedPlanFailureMessage(
      BookWorkflowBoundaryPhase phase,
      @Nullable BookWorkflowStepId triggerStepId,
      RuntimeException failure) {
    String detail = String.valueOf(failure.getMessage()).strip();
    String phaseContext;
    if (phase == BookWorkflowBoundaryPhase.BEGIN) {
      phaseContext = "during begin";
    } else if (phase == BookWorkflowBoundaryPhase.INITIALIZATION_CHECK) {
      phaseContext =
          triggerStepId == null
              ? "during initialization-check"
              : "during initialization-check before step '%s'".formatted(triggerStepId.value());
    } else if (phase == BookWorkflowBoundaryPhase.COMMIT) {
      phaseContext =
          triggerStepId == null
              ? "during commit"
              : "during commit after step '%s'".formatted(triggerStepId.value());
    } else {
      phaseContext =
          triggerStepId == null
              ? "during rollback"
              : "during rollback after step '%s'".formatted(triggerStepId.value());
    }
    if (detail.isEmpty() || "null".equals(detail)) {
      return "Ledger plan execution failed unexpectedly %s.".formatted(phaseContext);
    }
    return "Ledger plan execution failed unexpectedly %s: %s".formatted(phaseContext, detail);
  }

  private static void appendTriggerDescriptorFacts(
      List<BookWorkflowFact> facts, BookWorkflowJournalDescriptor descriptor) {
    var journalStep = BookWorkflowPublishedLanguageTranslator.toPublishedJournalStep(descriptor);
    facts.add(BookWorkflowFact.text("triggerStepKind", journalStep.kind().wireValue()));
    if (journalStep.detailKind() != null) {
      facts.add(BookWorkflowFact.text("triggerDetailKind", journalStep.detailKind().wireValue()));
    }
    if (descriptor instanceof BookWorkflowJournalDescriptor.Boundary boundary) {
      facts.add(BookWorkflowFact.text("triggerBoundaryPhase", boundary.phase().wireValue()));
    }
  }

  private static BookWorkflowStepId boundaryStepId(BookWorkflowBoundaryPhase phase) {
    return new BookWorkflowStepId("@plan-boundary:" + phase.wireValue());
  }
}
