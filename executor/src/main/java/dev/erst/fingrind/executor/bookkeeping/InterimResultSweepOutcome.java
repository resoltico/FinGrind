package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Sealed family of interim-result-sweep administration outcomes. */
public sealed interface InterimResultSweepOutcome
    permits InterimResultSweepOutcome.Transferred, InterimResultSweepOutcome.Rejected {

  /** Successful durable interim-result-sweep outcome carrying the stored sweep fact. */
  record Transferred(SweptInterimResult sweptInterimResult) implements InterimResultSweepOutcome {
    public Transferred {
      Objects.requireNonNull(sweptInterimResult, "sweptInterimResult");
    }
  }

  /** Deterministic interim-result-sweep rejection carrying one administration refusal. */
  record Rejected(BookkeepingAdministrationRejection rejection)
      implements InterimResultSweepOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
