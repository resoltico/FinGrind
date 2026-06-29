package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Sealed family of public interim-result-sweep outcomes. */
public sealed interface InterimResultSweepResult
    permits InterimResultSweepResult.Swept, InterimResultSweepResult.Rejected {

  /** Successful interim-result-sweep outcome carrying the durable sweep fact. */
  record Swept(SweptInterimResult sweptInterimResult) implements InterimResultSweepResult {
    public Swept {
      Objects.requireNonNull(sweptInterimResult, "sweptInterimResult");
    }
  }

  /** Interim-result-sweep outcome carrying one deterministic administration rejection. */
  record Rejected(BookAdministrationRejection rejection) implements InterimResultSweepResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
