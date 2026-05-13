package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Closed family of close-period administration outcomes. */
public sealed interface PeriodCloseOutcome
    permits PeriodCloseOutcome.Closed, PeriodCloseOutcome.Rejected {

  /** Successful durable period-close outcome carrying the stored closed-period fact. */
  record Closed(ClosedPeriod closedPeriod) implements PeriodCloseOutcome {
    public Closed {
      Objects.requireNonNull(closedPeriod, "closedPeriod");
    }
  }

  /** Deterministic close-period rejection carrying one administration refusal. */
  record Rejected(BookkeepingAdministrationRejection rejection) implements PeriodCloseOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
