package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.LedgerExecutionJournal;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanId;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStep;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes canonical AI-agent ledger plans against one atomic book session. */
public final class LedgerPlanService {
  private final LedgerPlanSession planSession;
  private final Clock clock;
  private final LedgerPlanStepExecutor stepExecutor;

  /** Creates a ledger-plan executor. */
  public LedgerPlanService(
      LedgerPlanSession bookSession, PostingIdGenerator postingIdGenerator, Clock clock) {
    this.planSession = Objects.requireNonNull(bookSession, "bookSession");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.stepExecutor =
        new LedgerPlanStepExecutor(planSession, postingIdGenerator, Objects.requireNonNull(clock));
  }

  /** Executes one plan atomically, committing only when every step succeeds. */
  public LedgerPlanResult execute(LedgerPlan plan) {
    Objects.requireNonNull(plan, "plan");
    Instant startedAt = Instant.now(clock);
    List<LedgerJournalEntry> entries = new ArrayList<>();
    planSession.beginLedgerPlanTransaction();
    boolean rollbackRequired = true;
    try {
      if (!plan.beginsWithOpenBook() && !stepExecutor.isInitialized()) {
        LedgerStep firstStep = plan.steps().getFirst();
        entries.add(stepExecutor.missingBookEntry(firstStep, startedAt));
        planSession.rollbackLedgerPlanTransaction();
        rollbackRequired = false;
        return result(plan.planId(), LedgerPlanStatus.REJECTED, startedAt, entries);
      }
      for (LedgerStep step : plan.steps()) {
        switch (stepExecutor.execute(step)) {
          case LedgerJournalEntry.Succeeded succeeded -> entries.add(succeeded);
          case LedgerJournalEntry.Rejected rejected -> {
            entries.add(rejected);
            planSession.rollbackLedgerPlanTransaction();
            rollbackRequired = false;
            return result(plan.planId(), LedgerPlanStatus.REJECTED, startedAt, entries);
          }
          case LedgerJournalEntry.AssertionFailed assertionFailed -> {
            entries.add(assertionFailed);
            planSession.rollbackLedgerPlanTransaction();
            rollbackRequired = false;
            return result(plan.planId(), LedgerPlanStatus.ASSERTION_FAILED, startedAt, entries);
          }
        }
      }
      planSession.commitLedgerPlanTransaction();
      rollbackRequired = false;
      return result(plan.planId(), LedgerPlanStatus.SUCCEEDED, startedAt, entries);
    } finally {
      if (rollbackRequired) {
        planSession.rollbackLedgerPlanTransaction();
      }
    }
  }

  private LedgerPlanResult result(
      LedgerPlanId planId,
      LedgerPlanStatus status,
      Instant startedAt,
      List<LedgerJournalEntry> entries) {
    LedgerExecutionJournal journal =
        new LedgerExecutionJournal(startedAt, Instant.now(clock), List.copyOf(entries));
    return switch (status) {
      case SUCCEEDED -> new LedgerPlanResult.Succeeded(planId, journal);
      case REJECTED -> new LedgerPlanResult.Rejected(planId, journal);
      case ASSERTION_FAILED -> new LedgerPlanResult.AssertionFailed(planId, journal);
    };
  }
}
