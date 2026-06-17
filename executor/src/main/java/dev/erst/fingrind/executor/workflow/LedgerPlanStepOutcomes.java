package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.List;
import java.util.Objects;

/** Successful, asserted, and caller-facing step outcomes for ledger-plan execution. */
public final class LedgerPlanStepOutcomes {
  private LedgerPlanStepOutcomes() {}

  /** Returns one successful workflow step outcome containing the canonical balance fact payload. */
  public static LedgerPlanStepOutcome balanceFacts(AccountBalanceView view) {
    return stepSucceeded(LedgerPlanFactMapper.balanceFacts(view));
  }

  /** Expands one committed posting into the canonical workflow fact payload. */
  public static List<BookWorkflowFact> postingFacts(CommittedPosting postingFact) {
    return LedgerPlanFactMapper.postingFacts(postingFact);
  }

  /** Creates one local assertion-failure outcome with the supplied workflow facts. */
  public static LedgerPlanStepOutcome assertionFailure(String message, BookWorkflowFact... facts) {
    return new LedgerPlanStepOutcome.AssertionFailed(
        new BookWorkflowFailure("assertion-failed", message, List.of(facts)));
  }

  /** Chooses the public-facing missing-book code that matches the first workflow step family. */
  public static String missingBookCode(BookWorkflowStep firstStep) {
    Objects.requireNonNull(firstStep, "firstStep");
    if (firstStep instanceof BookWorkflowStep.EnsureBook
        || firstStep instanceof BookWorkflowStep.DeclareAccount) {
      return "administration-book-not-initialized";
    }
    if (firstStep instanceof BookWorkflowStep.PreflightEntry
        || firstStep instanceof BookWorkflowStep.PostEntry) {
      return "posting-book-not-initialized";
    }
    return dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection
        .bookNotInitializedCode();
  }

  /** Creates one successful workflow step outcome from the supplied facts. */
  public static LedgerPlanStepOutcome stepSucceeded(BookWorkflowFact... facts) {
    return new LedgerPlanStepOutcome.Succeeded(List.of(facts));
  }

  /** Creates one successful workflow step outcome from the supplied fact list. */
  public static LedgerPlanStepOutcome stepSucceeded(List<BookWorkflowFact> facts) {
    return new LedgerPlanStepOutcome.Succeeded(facts);
  }

  /** Converts one setup-identity mismatch into the local workflow outcome model. */
  public static LedgerPlanStepOutcome ensureBookIdentityConflict(
      BookIdentity existingBookIdentity, BookIdentity requestedBookIdentity) {
    return new LedgerPlanStepOutcome.Rejected(
        LedgerPlanWorkflowFailureMapper.ensureBookIdentityConflict(
            existingBookIdentity, requestedBookIdentity));
  }
}
