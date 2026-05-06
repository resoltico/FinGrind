package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.executor.workflow.BookWorkflowFailure;
import java.util.List;
import java.util.Objects;

/** Internal value model for one executed ledger-plan step before journal materialization. */
sealed interface LedgerPlanStepOutcome
    permits LedgerPlanStepOutcome.Succeeded,
        LedgerPlanStepOutcome.Rejected,
        LedgerPlanStepOutcome.AssertionFailed {
  /** Facts observed while executing the step, regardless of the terminal outcome kind. */
  List<LedgerFact> facts();

  /** Successful step outcome carrying facts and no failure payload. */
  record Succeeded(List<LedgerFact> facts) implements LedgerPlanStepOutcome {
    public Succeeded {
      facts = facts == null ? List.of() : List.copyOf(facts);
    }
  }

  /** Rejected step outcome carrying the failure that will be written to the journal. */
  record Rejected(BookWorkflowFailure failure) implements LedgerPlanStepOutcome {
    public Rejected {
      Objects.requireNonNull(failure, "failure");
    }

    @Override
    public List<LedgerFact> facts() {
      return failure.facts();
    }
  }

  /** Assertion-failed step outcome carrying the assertion failure payload. */
  record AssertionFailed(BookWorkflowFailure failure) implements LedgerPlanStepOutcome {
    public AssertionFailed {
      Objects.requireNonNull(failure, "failure");
    }

    @Override
    public List<LedgerFact> facts() {
      return failure.facts();
    }
  }
}
