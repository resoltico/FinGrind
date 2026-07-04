package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Sealed family of fiscal-year-close administration outcomes. */
public sealed interface FiscalYearCloseOutcome
    permits FiscalYearCloseOutcome.Closed, FiscalYearCloseOutcome.Rejected {

  /** Successful durable fiscal-year-close outcome carrying the stored close fact. */
  record Closed(ClosedFiscalYearRecord closedFiscalYear, boolean idempotentReplay)
      implements FiscalYearCloseOutcome {
    public Closed {
      Objects.requireNonNull(closedFiscalYear, "closedFiscalYear");
    }
  }

  /** Deterministic fiscal-year-close rejection carrying one administration refusal. */
  record Rejected(BookkeepingAdministrationRejection rejection) implements FiscalYearCloseOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
