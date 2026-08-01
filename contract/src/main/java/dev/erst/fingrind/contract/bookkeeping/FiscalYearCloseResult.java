package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Sealed family of public fiscal-year-close outcomes. */
public sealed interface FiscalYearCloseResult
    permits FiscalYearCloseResult.Closed, FiscalYearCloseResult.Rejected {

  /** Successful fiscal-year-close outcome carrying the durable close fact. */
  record Closed(
      ClosedFiscalYear closedFiscalYear,
      boolean idempotentReplay,
      @Nullable AttestationCommit attestationCommit)
      implements FiscalYearCloseResult {
    public Closed {
      Objects.requireNonNull(closedFiscalYear, "closedFiscalYear");
      if (idempotentReplay && attestationCommit != null) {
        throw new IllegalArgumentException(
            "An idempotent fiscal year close replay must not report a newly appended attestation operation.");
      }
      if (!idempotentReplay && attestationCommit == null) {
        throw new IllegalArgumentException(
            "A newly closed fiscal year must report its attestation operation.");
      }
    }
  }

  /** Fiscal-year-close outcome carrying one deterministic administration rejection. */
  record Rejected(BookAdministrationRejection rejection) implements FiscalYearCloseResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
