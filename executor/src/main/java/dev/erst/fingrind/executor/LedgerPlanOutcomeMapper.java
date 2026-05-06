package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerJournalStep;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.RejectionNarrative;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.workflow.BookWorkflowBoundaryPhase;
import dev.erst.fingrind.executor.workflow.BookWorkflowFailure;
import dev.erst.fingrind.executor.workflow.BookWorkflowJournalDescriptor;
import dev.erst.fingrind.executor.workflow.BookWorkflowJournalEntry;
import dev.erst.fingrind.executor.workflow.BookWorkflowPublishedLanguageTranslator;
import dev.erst.fingrind.executor.workflow.BookWorkflowStep;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared fact and failure mapping for ledger-plan execution steps. */
final class LedgerPlanOutcomeMapper {
  private LedgerPlanOutcomeMapper() {}

  static LedgerPlanStepOutcome balanceFacts(AccountBalanceView view) {
    return stepSucceeded(LedgerPlanFactMapper.balanceFacts(view));
  }

  static List<LedgerFact> postingFacts(CommittedPosting postingFact) {
    return LedgerPlanFactMapper.postingFacts(postingFact);
  }

  static LedgerPlanStepOutcome administrationRejection(
      BookkeepingAdministrationRejection rejection) {
    dev.erst.fingrind.contract.BookAdministrationRejection publishedRejection =
        BookkeepingPublishedLanguageTranslator.toPublished(rejection);
    return stepRejected(
        dev.erst.fingrind.contract.BookAdministrationRejection.wireCode(publishedRejection),
        RejectionNarrative.message(publishedRejection),
        RejectionNarrative.facts(publishedRejection));
  }

  static LedgerPlanStepOutcome queryRejection(
      dev.erst.fingrind.contract.BookQueryRejection rejection) {
    return stepRejected(
        dev.erst.fingrind.contract.BookQueryRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        RejectionNarrative.facts(rejection));
  }

  static LedgerPlanStepOutcome postingRejection(PostingRejection rejection) {
    return stepRejected(
        PostingRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        RejectionNarrative.facts(rejection));
  }

  static LedgerPlanStepOutcome assertionFailure(String message, LedgerFact... facts) {
    return new LedgerPlanStepOutcome.AssertionFailed(
        new BookWorkflowFailure("assertion-failed", message, List.of(facts)));
  }

  static String missingBookCode(BookWorkflowStep firstStep) {
    Objects.requireNonNull(firstStep, "firstStep");
    if (firstStep instanceof BookWorkflowStep.OpenBook
        || firstStep instanceof BookWorkflowStep.DeclareAccount) {
      return dev.erst.fingrind.contract.BookAdministrationRejection.bookNotInitializedCode();
    }
    if (firstStep instanceof BookWorkflowStep.PreflightEntry
        || firstStep instanceof BookWorkflowStep.PostEntry) {
      return PostingRejection.bookNotInitializedCode();
    }
    return dev.erst.fingrind.contract.BookQueryRejection.bookNotInitializedCode();
  }

  static LedgerPlanStepOutcome stepSucceeded(LedgerFact... facts) {
    return new LedgerPlanStepOutcome.Succeeded(List.of(facts));
  }

  static LedgerPlanStepOutcome stepSucceeded(List<LedgerFact> facts) {
    return new LedgerPlanStepOutcome.Succeeded(facts);
  }

  static LedgerPlanStepOutcome stepRejected(String code, String message, List<LedgerFact> facts) {
    return new LedgerPlanStepOutcome.Rejected(new BookWorkflowFailure(code, message, facts));
  }

  static BookWorkflowJournalEntry.Rejected unexpectedExecutionFailure(
      BookWorkflowStep step, Instant startedAt, Instant finishedAt, RuntimeException failure) {
    return new BookWorkflowJournalEntry.Rejected(
        step.stepId(),
        new BookWorkflowJournalDescriptor.Step(step),
        startedAt,
        finishedAt,
        List.of(LedgerFact.text("exceptionType", failure.getClass().getName())),
        new BookWorkflowFailure(
            "unexpected-step-failure",
            unexpectedExecutionFailureMessage(step, failure),
            List.of(LedgerFact.text("exceptionType", failure.getClass().getName()))));
  }

  static BookWorkflowJournalEntry.Rejected unexpectedPlanFailure(
      BookWorkflowBoundaryPhase phase,
      Instant startedAt,
      Instant finishedAt,
      @Nullable String triggerStepId,
      @Nullable BookWorkflowJournalDescriptor triggerDescriptor,
      RuntimeException failure,
      @Nullable RuntimeException cleanupFailure,
      @Nullable BookWorkflowFailure priorFailure) {
    List<LedgerFact> failureFacts = new ArrayList<>();
    failureFacts.add(LedgerFact.text("phase", phase.wireValue()));
    failureFacts.add(LedgerFact.text("exceptionType", failure.getClass().getName()));
    if (triggerStepId != null) {
      failureFacts.add(LedgerFact.text("triggerStepId", triggerStepId));
    }
    if (triggerDescriptor != null) {
      appendTriggerDescriptorFacts(failureFacts, triggerDescriptor);
    }
    if (cleanupFailure != null) {
      failureFacts.add(
          LedgerFact.group(
              "cleanupFailure",
              List.of(LedgerFact.text("exceptionType", cleanupFailure.getClass().getName()))));
    }
    if (priorFailure != null) {
      failureFacts.add(
          LedgerFact.group(
              "priorFailure",
              List.of(
                  LedgerFact.text("code", priorFailure.code()),
                  LedgerFact.text("message", priorFailure.message()))));
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
      return "Ledger plan execution failed unexpectedly during step '%s'.".formatted(step.stepId());
    }
    return "Ledger plan execution failed unexpectedly during step '%s': %s"
        .formatted(step.stepId(), detail);
  }

  private static String unexpectedPlanFailureMessage(
      BookWorkflowBoundaryPhase phase, @Nullable String triggerStepId, RuntimeException failure) {
    String detail = String.valueOf(failure.getMessage()).strip();
    String phaseContext =
        switch (phase) {
          case BEGIN -> "during begin";
          case INITIALIZATION_CHECK ->
              triggerStepId == null
                  ? "during initialization-check"
                  : "during initialization-check before step '%s'".formatted(triggerStepId);
          case COMMIT ->
              triggerStepId == null
                  ? "during commit"
                  : "during commit after step '%s'".formatted(triggerStepId);
          case ROLLBACK ->
              triggerStepId == null
                  ? "during rollback"
                  : "during rollback after step '%s'".formatted(triggerStepId);
        };
    if (detail.isEmpty() || "null".equals(detail)) {
      return "Ledger plan execution failed unexpectedly %s.".formatted(phaseContext);
    }
    return "Ledger plan execution failed unexpectedly %s: %s".formatted(phaseContext, detail);
  }

  private static void appendTriggerDescriptorFacts(
      List<LedgerFact> facts, BookWorkflowJournalDescriptor descriptor) {
    LedgerJournalStep journalStep =
        BookWorkflowPublishedLanguageTranslator.toPublishedJournalStep(descriptor);
    facts.add(LedgerFact.text("triggerStepKind", journalStep.kind().wireValue()));
    if (journalStep.detailKind() != null) {
      facts.add(LedgerFact.text("triggerDetailKind", journalStep.detailKind().wireValue()));
    }
    if (descriptor instanceof BookWorkflowJournalDescriptor.Boundary boundary) {
      facts.add(LedgerFact.text("triggerBoundaryPhase", boundary.phase().wireValue()));
    }
  }

  private static String boundaryStepId(BookWorkflowBoundaryPhase phase) {
    return "@plan-boundary:" + phase.wireValue();
  }
}
