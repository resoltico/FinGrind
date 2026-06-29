package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Sealed family of public fiscal-year-close outcomes. */
public sealed interface FiscalYearCloseResult
    permits FiscalYearCloseResult.Closed, FiscalYearCloseResult.Rejected {

  /** Successful fiscal-year-close outcome carrying the durable close fact. */
  record Closed(ClosedFiscalYear closedFiscalYear) implements FiscalYearCloseResult {
    public Closed {
      Objects.requireNonNull(closedFiscalYear, "closedFiscalYear");
    }
  }

  /** Fiscal-year-close outcome carrying one deterministic administration rejection. */
  record Rejected(BookAdministrationRejection rejection) implements FiscalYearCloseResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
