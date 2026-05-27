package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared fact and failure mapping for ledger-plan execution steps. */
public final class LedgerPlanOutcomeMapper {
  private LedgerPlanOutcomeMapper() {}

  /** Returns one successful workflow step outcome containing the canonical balance fact payload. */
  public static LedgerPlanStepOutcome balanceFacts(AccountBalanceView view) {
    return stepSucceeded(LedgerPlanFactMapper.balanceFacts(view));
  }

  /** Expands one committed posting into the canonical workflow fact payload. */
  public static List<BookWorkflowFact> postingFacts(CommittedPosting postingFact) {
    return LedgerPlanFactMapper.postingFacts(postingFact);
  }

  /** Converts one local administration rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome administrationRejection(
      BookkeepingAdministrationRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(administrationFailure(rejection));
  }

  /** Converts one local query rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome queryRejection(BookkeepingQueryRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(queryFailure(rejection));
  }

  /** Converts one local posting rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome postingRejection(BookkeepingPostingRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(postingFailure(rejection));
  }

  /** Converts one published posting rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome postingRejection(PostingRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(postingFailure(rejection));
  }

  /** Creates one local assertion-failure outcome with the supplied workflow facts. */
  public static LedgerPlanStepOutcome assertionFailure(String message, BookWorkflowFact... facts) {
    return new LedgerPlanStepOutcome.AssertionFailed(
        new BookWorkflowFailure("assertion-failed", message, List.of(facts)));
  }

  /** Chooses the public-facing missing-book code that matches the first workflow step family. */
  public static String missingBookCode(BookWorkflowStep firstStep) {
    Objects.requireNonNull(firstStep, "firstStep");
    if (firstStep instanceof BookWorkflowStep.OpenBook
        || firstStep instanceof BookWorkflowStep.DeclareAccount) {
      return "administration-book-not-initialized";
    }
    if (firstStep instanceof BookWorkflowStep.PreflightEntry
        || firstStep instanceof BookWorkflowStep.PostEntry) {
      return "posting-book-not-initialized";
    }
    return BookkeepingQueryRejection.bookNotInitializedCode();
  }

  /** Creates one successful workflow step outcome from the supplied facts. */
  public static LedgerPlanStepOutcome stepSucceeded(BookWorkflowFact... facts) {
    return new LedgerPlanStepOutcome.Succeeded(List.of(facts));
  }

  /** Creates one successful workflow step outcome from the supplied fact list. */
  public static LedgerPlanStepOutcome stepSucceeded(List<BookWorkflowFact> facts) {
    return new LedgerPlanStepOutcome.Succeeded(facts);
  }

  /** Wraps one unexpected step exception into the local rejected journal-entry form. */
  public static BookWorkflowJournalEntry.Rejected unexpectedExecutionFailure(
      BookWorkflowStep step, Instant startedAt, Instant finishedAt, RuntimeException failure) {
    return LedgerPlanUnexpectedFailureMapper.unexpectedExecutionFailure(
        step, startedAt, finishedAt, failure);
  }

  /** Wraps one unexpected plan-boundary exception into the local rejected journal-entry form. */
  public static BookWorkflowJournalEntry.Rejected unexpectedPlanFailure(
      BookWorkflowBoundaryPhase phase,
      Instant startedAt,
      Instant finishedAt,
      @Nullable BookWorkflowStepId triggerStepId,
      @Nullable BookWorkflowJournalDescriptor triggerDescriptor,
      RuntimeException failure,
      @Nullable RuntimeException cleanupFailure,
      @Nullable BookWorkflowFailure priorFailure) {
    return LedgerPlanUnexpectedFailureMapper.unexpectedPlanFailure(
        phase,
        startedAt,
        finishedAt,
        triggerStepId,
        triggerDescriptor,
        failure,
        cleanupFailure,
        priorFailure);
  }

  private static BookWorkflowFailure administrationFailure(
      BookkeepingAdministrationRejection rejection) {
    return LedgerPlanWorkflowFailureMapper.administrationFailure(rejection);
  }

  private static BookWorkflowFailure queryFailure(BookkeepingQueryRejection rejection) {
    return LedgerPlanWorkflowFailureMapper.queryFailure(rejection);
  }

  private static BookWorkflowFailure postingFailure(BookkeepingPostingRejection rejection) {
    return LedgerPlanWorkflowFailureMapper.postingFailure(rejection);
  }

  private static BookWorkflowFailure postingFailure(PostingRejection publishedRejection) {
    return LedgerPlanWorkflowFailureMapper.postingFailure(publishedRejection);
  }
}
