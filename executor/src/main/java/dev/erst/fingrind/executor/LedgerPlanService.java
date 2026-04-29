package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.LedgerExecutionJournal;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerJournalStep;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanId;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.LedgerStepId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Executes canonical AI-agent ledger plans against one atomic book session. */
public final class LedgerPlanService {
  /** Immutable context for one plan-boundary failure that must produce a terminal journal entry. */
  private record BoundaryFailureContext(
      LedgerPlanId planId,
      Instant planStartedAt,
      List<LedgerJournalEntry> entries,
      LedgerBoundaryPhase phase,
      Instant phaseStartedAt,
      @Nullable LedgerStepId triggerStepId,
      @Nullable LedgerJournalStep triggerJournalStep) {
    private BoundaryFailureContext {
      Objects.requireNonNull(planId, "planId");
      Objects.requireNonNull(planStartedAt, "planStartedAt");
      Objects.requireNonNull(entries, "entries");
      Objects.requireNonNull(phase, "phase");
      Objects.requireNonNull(phaseStartedAt, "phaseStartedAt");
    }

    private static BoundaryFailureContext begin(
        LedgerPlanId planId, Instant planStartedAt, List<LedgerJournalEntry> entries) {
      return new BoundaryFailureContext(
          planId, planStartedAt, entries, LedgerBoundaryPhase.BEGIN, planStartedAt, null, null);
    }

    private static BoundaryFailureContext beforeStep(
        LedgerPlanId planId,
        Instant planStartedAt,
        List<LedgerJournalEntry> entries,
        LedgerBoundaryPhase phase,
        LedgerStep step,
        Instant phaseStartedAt) {
      Objects.requireNonNull(step, "step");
      return new BoundaryFailureContext(
          planId, planStartedAt, entries, phase, phaseStartedAt, step.stepId(), step.journalStep());
    }

    private static BoundaryFailureContext afterJournalEntry(
        LedgerPlanId planId,
        Instant planStartedAt,
        List<LedgerJournalEntry> entries,
        LedgerBoundaryPhase phase,
        LedgerJournalEntry entry,
        Instant phaseStartedAt) {
      Objects.requireNonNull(entry, "entry");
      return new BoundaryFailureContext(
          planId,
          planStartedAt,
          entries,
          phase,
          phaseStartedAt,
          entry.stepId(),
          entry.journalStep());
    }
  }

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
    List<LedgerStep> steps = plan.steps();
    LedgerStep firstStep = steps.getFirst();

    try {
      planSession.beginLedgerPlanTransaction();
    } catch (RuntimeException exception) {
      return boundaryFailureResult(
          BoundaryFailureContext.begin(plan.planId(), startedAt, entries), exception, null, null);
    }

    if (!plan.beginsWithOpenBook()) {
      try {
        if (!stepExecutor.isInitialized()) {
          return failedResultWithRollback(
              plan.planId(),
              startedAt,
              entries,
              stepExecutor.missingBookEntry(firstStep, startedAt));
        }
      } catch (RuntimeException exception) {
        return boundaryFailureAfterRollback(
            BoundaryFailureContext.beforeStep(
                plan.planId(),
                startedAt,
                entries,
                LedgerBoundaryPhase.INITIALIZATION_CHECK,
                firstStep,
                Instant.now(clock)),
            exception,
            null);
      }
    }

    LedgerJournalEntry.Succeeded pendingSuccessfulStep = null;
    for (LedgerStep step : steps) {
      Instant stepStartedAt = Instant.now(clock);
      LedgerJournalEntry stepEntry;
      try {
        stepEntry = stepExecutor.execute(step);
      } catch (RuntimeException exception) {
        if (pendingSuccessfulStep != null) {
          entries.add(pendingSuccessfulStep);
        }
        RuntimeException rollbackFailure = rollbackFailure();
        if (rollbackFailure != null) {
          LedgerStepFailure priorFailure =
              LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
                      step, stepStartedAt, Instant.now(clock), exception)
                  .requiredFailure();
          return boundaryFailureResult(
              BoundaryFailureContext.afterJournalEntry(
                  plan.planId(),
                  startedAt,
                  entries,
                  LedgerBoundaryPhase.ROLLBACK,
                  LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
                      step, stepStartedAt, Instant.now(clock), exception),
                  Instant.now(clock)),
              rollbackFailure,
              null,
              priorFailure);
        }
        entries.add(
            LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
                step, stepStartedAt, Instant.now(clock), exception));
        return result(plan.planId(), LedgerPlanStatus.REJECTED, startedAt, entries);
      }
      switch (stepEntry) {
        case LedgerJournalEntry.Succeeded succeeded -> {
          if (pendingSuccessfulStep != null) {
            entries.add(pendingSuccessfulStep);
          }
          pendingSuccessfulStep = succeeded;
        }
        case LedgerJournalEntry.Failed failed -> {
          if (pendingSuccessfulStep != null) {
            entries.add(pendingSuccessfulStep);
          }
          return failedResultWithRollback(plan.planId(), startedAt, entries, failed);
        }
      }
    }

    Objects.requireNonNull(pendingSuccessfulStep, "pendingSuccessfulStep");
    Instant commitStartedAt = Instant.now(clock);
    try {
      planSession.commitLedgerPlanTransaction();
    } catch (RuntimeException exception) {
      return boundaryFailureAfterRollback(
          BoundaryFailureContext.afterJournalEntry(
              plan.planId(),
              startedAt,
              entries,
              LedgerBoundaryPhase.COMMIT,
              pendingSuccessfulStep,
              commitStartedAt),
          exception,
          null);
    }
    entries.add(pendingSuccessfulStep);
    return result(plan.planId(), LedgerPlanStatus.SUCCEEDED, startedAt, entries);
  }

  private LedgerPlanResult failedResultWithRollback(
      LedgerPlanId planId,
      Instant startedAt,
      List<LedgerJournalEntry> entries,
      LedgerJournalEntry.Failed failed) {
    RuntimeException rollbackFailure = rollbackFailure();
    if (rollbackFailure != null) {
      return boundaryFailureResult(
          BoundaryFailureContext.afterJournalEntry(
              planId, startedAt, entries, LedgerBoundaryPhase.ROLLBACK, failed, Instant.now(clock)),
          rollbackFailure,
          null,
          failed.requiredFailure());
    }
    entries.add(failed);
    return result(planId, failedStatus(failed), startedAt, entries);
  }

  private LedgerPlanResult boundaryFailureAfterRollback(
      BoundaryFailureContext context,
      RuntimeException exception,
      @Nullable LedgerStepFailure priorFailure) {
    RuntimeException rollbackFailure = rollbackFailure();
    return boundaryFailureResult(context, exception, rollbackFailure, priorFailure);
  }

  private LedgerPlanResult boundaryFailureResult(
      BoundaryFailureContext context,
      RuntimeException exception,
      @Nullable RuntimeException cleanupFailure,
      @Nullable LedgerStepFailure priorFailure) {
    context
        .entries()
        .add(
            LedgerPlanOutcomeMapper.unexpectedPlanFailure(
                context.phase(),
                context.phaseStartedAt(),
                Instant.now(clock),
                context.triggerStepId(),
                context.triggerJournalStep(),
                exception,
                cleanupFailure,
                priorFailure));
    return result(
        context.planId(), LedgerPlanStatus.REJECTED, context.planStartedAt(), context.entries());
  }

  private @Nullable RuntimeException rollbackFailure() {
    try {
      planSession.rollbackLedgerPlanTransaction();
      return null;
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private static LedgerPlanStatus failedStatus(LedgerJournalEntry.Failed failed) {
    return switch (failed) {
      case LedgerJournalEntry.Rejected _ -> LedgerPlanStatus.REJECTED;
      case LedgerJournalEntry.AssertionFailed _ -> LedgerPlanStatus.ASSERTION_FAILED;
    };
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
