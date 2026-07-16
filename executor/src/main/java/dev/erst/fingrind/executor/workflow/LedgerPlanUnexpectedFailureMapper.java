package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.workflow.LedgerPlanFailure;
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
            LedgerPlanFailure.UNEXPECTED_STEP_FAILURE.code(),
            unexpectedExecutionFailureMessage(step, failure),
            facts));
  }

  static BookWorkflowJournalEntry.Rejected unexpectedPlanFailure(
      BookWorkflowBoundaryCheckpoint checkpoint,
      Instant startedAt,
      Instant finishedAt,
      @Nullable BookWorkflowStepId triggerStepId,
      @Nullable BookWorkflowJournalDescriptor triggerDescriptor,
      RuntimeException failure,
      @Nullable RuntimeException cleanupFailure,
      @Nullable BookWorkflowFailure priorFailure) {
    List<BookWorkflowFact> failureFacts = new ArrayList<>();
    failureFacts.add(BookWorkflowFact.text("checkpoint", checkpoint.wireValue()));
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
        boundaryStepId(checkpoint),
        new BookWorkflowJournalDescriptor.Boundary(checkpoint),
        startedAt,
        finishedAt,
        List.of(),
        new BookWorkflowFailure(
            LedgerPlanFailure.UNEXPECTED_PLAN_FAILURE.code(),
            unexpectedPlanFailureMessage(checkpoint, triggerStepId, failure),
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
      BookWorkflowBoundaryCheckpoint checkpoint,
      @Nullable BookWorkflowStepId triggerStepId,
      RuntimeException failure) {
    String detail = String.valueOf(failure.getMessage()).strip();
    String checkpointContext;
    if (checkpoint == BookWorkflowBoundaryCheckpoint.BEGIN) {
      checkpointContext = "during begin";
    } else if (checkpoint == BookWorkflowBoundaryCheckpoint.INITIALIZATION_CHECK) {
      checkpointContext =
          triggerStepId == null
              ? "during initialization-check"
              : "during initialization-check before step '%s'".formatted(triggerStepId.value());
    } else if (checkpoint == BookWorkflowBoundaryCheckpoint.COMMIT) {
      checkpointContext =
          triggerStepId == null
              ? "during commit"
              : "during commit after step '%s'".formatted(triggerStepId.value());
    } else {
      checkpointContext =
          triggerStepId == null
              ? "during rollback"
              : "during rollback after step '%s'".formatted(triggerStepId.value());
    }
    if (detail.isEmpty() || "null".equals(detail)) {
      return "Ledger plan execution failed unexpectedly %s.".formatted(checkpointContext);
    }
    return "Ledger plan execution failed unexpectedly %s: %s".formatted(checkpointContext, detail);
  }

  private static void appendTriggerDescriptorFacts(
      List<BookWorkflowFact> facts, BookWorkflowJournalDescriptor descriptor) {
    var journalStep = BookWorkflowPublishedLanguageTranslator.toPublishedJournalStep(descriptor);
    facts.add(BookWorkflowFact.text("triggerStepKind", journalStep.kind().wireValue()));
    if (journalStep.detailKind() != null) {
      facts.add(BookWorkflowFact.text("triggerDetailKind", journalStep.detailKind().wireValue()));
    }
    if (descriptor instanceof BookWorkflowJournalDescriptor.Boundary boundary) {
      facts.add(
          BookWorkflowFact.text("triggerBoundaryCheckpoint", boundary.checkpoint().wireValue()));
    }
  }

  private static BookWorkflowStepId boundaryStepId(BookWorkflowBoundaryCheckpoint checkpoint) {
    return new BookWorkflowStepId("@plan-boundary:" + checkpoint.wireValue());
  }
}
