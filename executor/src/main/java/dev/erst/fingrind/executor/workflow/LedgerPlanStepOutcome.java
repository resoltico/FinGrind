package dev.erst.fingrind.executor.workflow;

import java.util.List;
import java.util.Objects;

/** Internal value model for one executed ledger-plan step before journal materialization. */
public sealed interface LedgerPlanStepOutcome
    permits LedgerPlanStepOutcome.Succeeded,
        LedgerPlanStepOutcome.Rejected,
        LedgerPlanStepOutcome.AssertionFailed {
  /** Facts observed while executing the step, regardless of the terminal outcome kind. */
  List<BookWorkflowFact> facts();

  /** Successful step outcome carrying facts and no failure payload. */
  record Succeeded(List<BookWorkflowFact> facts) implements LedgerPlanStepOutcome {
    public Succeeded {
      facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
    }
  }

  /** Rejected step outcome carrying the failure that will be written to the journal. */
  record Rejected(BookWorkflowFailure failure) implements LedgerPlanStepOutcome {
    public Rejected {
      Objects.requireNonNull(failure, "failure");
    }

    @Override
    public List<BookWorkflowFact> facts() {
      return failure.facts();
    }
  }

  /** Assertion-failed step outcome carrying the assertion failure payload. */
  record AssertionFailed(BookWorkflowFailure failure) implements LedgerPlanStepOutcome {
    public AssertionFailed {
      Objects.requireNonNull(failure, "failure");
    }

    @Override
    public List<BookWorkflowFact> facts() {
      return failure.facts();
    }
  }
}
