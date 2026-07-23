package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Sealed family of interim-result-sweep administration outcomes. */
public sealed interface InterimResultSweepOutcome
    permits InterimResultSweepOutcome.Transferred, InterimResultSweepOutcome.Rejected {

  /** Successful durable interim-result-sweep outcome carrying the stored sweep fact. */
  record Transferred(
      SweptInterimResult sweptInterimResult, @Nullable AttestationCommit attestationCommit)
      implements InterimResultSweepOutcome {
    public Transferred {
      Objects.requireNonNull(sweptInterimResult, "sweptInterimResult");
    }

    /** Creates a pre-persistence sweep decision with no attestation append yet. */
    public Transferred(SweptInterimResult sweptInterimResult) {
      this(sweptInterimResult, null);
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
