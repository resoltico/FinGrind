package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;

/** Rejected ledger-plan outcomes mapped from administration, query, and posting failures. */
public final class LedgerPlanRejectedOutcomes {
  private LedgerPlanRejectedOutcomes() {}

  /** Converts one local administration rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome administrationRejection(
      BookkeepingAdministrationRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(
        LedgerPlanWorkflowFailureMapper.administrationFailure(rejection));
  }

  /** Converts one local query rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome queryRejection(BookkeepingQueryRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(
        LedgerPlanWorkflowFailureMapper.queryFailure(rejection));
  }

  /** Converts one local posting rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome postingRejection(BookkeepingPostingRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(
        LedgerPlanWorkflowFailureMapper.postingFailure(rejection));
  }

  /** Converts one published posting rejection into the local workflow outcome model. */
  public static LedgerPlanStepOutcome postingRejection(PostingRejection rejection) {
    return new LedgerPlanStepOutcome.Rejected(
        LedgerPlanWorkflowFailureMapper.postingFailure(rejection));
  }
}
