package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerJournalStep;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.LedgerStepId;
import dev.erst.fingrind.contract.LedgerStepStatus;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.RejectionNarrative;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared fact and failure mapping for ledger-plan execution steps. */
final class LedgerPlanOutcomeMapper {
  private LedgerPlanOutcomeMapper() {}

  static LedgerPlanStepOutcome balanceFacts(AccountBalanceSnapshot snapshot) {
    return stepSucceeded(LedgerPlanFactMapper.balanceFacts(snapshot));
  }

  static List<LedgerFact> postingFacts(PostingFact postingFact) {
    return LedgerPlanFactMapper.postingFacts(postingFact);
  }

  static LedgerPlanStepOutcome administrationRejection(
      dev.erst.fingrind.contract.BookAdministrationRejection rejection) {
    return stepRejected(
        dev.erst.fingrind.contract.BookAdministrationRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        RejectionNarrative.facts(rejection));
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
        new LedgerStepFailure(
            LedgerStepStatus.ASSERTION_FAILED.wireValue(), message, List.of(facts)));
  }

  static String missingBookCode(LedgerStep firstStep) {
    return switch (firstStep.kind()) {
      case OPEN_BOOK, DECLARE_ACCOUNT ->
          dev.erst.fingrind.contract.BookAdministrationRejection.bookNotInitializedCode();
      case PREFLIGHT_ENTRY, POST_ENTRY -> PostingRejection.bookNotInitializedCode();
      case INSPECT_BOOK, LIST_ACCOUNTS, GET_POSTING, LIST_POSTINGS, ACCOUNT_BALANCE, ASSERT ->
          dev.erst.fingrind.contract.BookQueryRejection.bookNotInitializedCode();
    };
  }

  static LedgerPlanStepOutcome stepSucceeded(LedgerFact... facts) {
    return new LedgerPlanStepOutcome.Succeeded(List.of(facts));
  }

  static LedgerPlanStepOutcome stepSucceeded(List<LedgerFact> facts) {
    return new LedgerPlanStepOutcome.Succeeded(facts);
  }

  static LedgerPlanStepOutcome stepRejected(String code, String message, List<LedgerFact> facts) {
    return new LedgerPlanStepOutcome.Rejected(new LedgerStepFailure(code, message, facts));
  }

  static LedgerJournalEntry.Rejected unexpectedExecutionFailure(
      LedgerStep step, Instant startedAt, Instant finishedAt, RuntimeException failure) {
    return new LedgerJournalEntry.Rejected(
        step.stepId(),
        step.journalStep(),
        startedAt,
        finishedAt,
        List.of(LedgerFact.text("exceptionType", failure.getClass().getName())),
        new LedgerStepFailure(
            "unexpected-step-failure",
            unexpectedExecutionFailureMessage(step, failure),
            List.of(LedgerFact.text("exceptionType", failure.getClass().getName()))));
  }

  static LedgerJournalEntry.Rejected unexpectedPlanFailure(
      LedgerBoundaryPhase phase,
      Instant startedAt,
      Instant finishedAt,
      @Nullable LedgerStepId triggerStepId,
      @Nullable LedgerJournalStep triggerJournalStep,
      RuntimeException failure,
      @Nullable RuntimeException cleanupFailure,
      @Nullable LedgerStepFailure priorFailure) {
    List<LedgerFact> failureFacts = new ArrayList<>();
    failureFacts.add(LedgerFact.text("phase", phase.wireValue()));
    failureFacts.add(LedgerFact.text("exceptionType", failure.getClass().getName()));
    if (triggerStepId != null) {
      failureFacts.add(LedgerFact.text("triggerStepId", triggerStepId.value()));
    }
    if (triggerJournalStep != null) {
      failureFacts.add(LedgerFact.text("triggerStepKind", triggerJournalStep.kind().wireValue()));
      if (triggerJournalStep.detailKind() != null) {
        failureFacts.add(
            LedgerFact.text("triggerDetailKind", triggerJournalStep.detailKind().wireValue()));
      }
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
    return new LedgerJournalEntry.Rejected(
        boundaryStepId(phase),
        LedgerJournalStep.boundary(phase),
        startedAt,
        finishedAt,
        List.of(),
        new LedgerStepFailure(
            "unexpected-plan-failure",
            unexpectedPlanFailureMessage(phase, triggerStepId, failure),
            failureFacts));
  }

  private static String unexpectedExecutionFailureMessage(
      LedgerStep step, RuntimeException failure) {
    String detail = String.valueOf(failure.getMessage()).strip();
    if (detail.isEmpty() || "null".equals(detail)) {
      return "Ledger plan execution failed unexpectedly during step '%s'."
          .formatted(step.stepId().value());
    }
    return "Ledger plan execution failed unexpectedly during step '%s': %s"
        .formatted(step.stepId().value(), detail);
  }

  private static String unexpectedPlanFailureMessage(
      LedgerBoundaryPhase phase, @Nullable LedgerStepId triggerStepId, RuntimeException failure) {
    String detail = String.valueOf(failure.getMessage()).strip();
    String phaseContext =
        switch (phase) {
          case BEGIN -> "during begin";
          case INITIALIZATION_CHECK ->
              triggerStepId == null
                  ? "during initialization-check"
                  : "during initialization-check before step '%s'".formatted(triggerStepId.value());
          case COMMIT ->
              triggerStepId == null
                  ? "during commit"
                  : "during commit after step '%s'".formatted(triggerStepId.value());
          case ROLLBACK ->
              triggerStepId == null
                  ? "during rollback"
                  : "during rollback after step '%s'".formatted(triggerStepId.value());
        };
    if (detail.isEmpty() || "null".equals(detail)) {
      return "Ledger plan execution failed unexpectedly %s.".formatted(phaseContext);
    }
    return "Ledger plan execution failed unexpectedly %s: %s".formatted(phaseContext, detail);
  }

  private static LedgerStepId boundaryStepId(LedgerBoundaryPhase phase) {
    return new LedgerStepId("@plan-boundary:" + phase.wireValue());
  }
}
