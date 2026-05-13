package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Closed family of public close-period outcomes. */
public sealed interface ClosePeriodResult
    permits ClosePeriodResult.Closed, ClosePeriodResult.Rejected {

  /** Successful close-period outcome carrying the durable closed-period fact. */
  record Closed(ClosedPeriod closedPeriod) implements ClosePeriodResult {
    public Closed {
      Objects.requireNonNull(closedPeriod, "closedPeriod");
    }
  }

  /** Close-period outcome carrying one deterministic administration rejection. */
  record Rejected(BookAdministrationRejection rejection) implements ClosePeriodResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
