package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Sealed family of fiscal-year-close administration outcomes. */
public sealed interface FiscalYearCloseOutcome
    permits FiscalYearCloseOutcome.Closed, FiscalYearCloseOutcome.Rejected {

  /** Successful durable fiscal-year-close outcome carrying the stored close fact. */
  record Closed(
      ClosedFiscalYearRecord closedFiscalYear,
      boolean idempotentReplay,
      @Nullable AttestationCommit attestationCommit)
      implements FiscalYearCloseOutcome {
    public Closed {
      Objects.requireNonNull(closedFiscalYear, "closedFiscalYear");
    }

    /** Creates a local close outcome before an attestation append has been observed. */
    public Closed(ClosedFiscalYearRecord closedFiscalYear, boolean idempotentReplay) {
      this(closedFiscalYear, idempotentReplay, null);
    }
  }

  /** Deterministic fiscal-year-close rejection carrying one administration refusal. */
  record Rejected(BookkeepingAdministrationRejection rejection) implements FiscalYearCloseOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
