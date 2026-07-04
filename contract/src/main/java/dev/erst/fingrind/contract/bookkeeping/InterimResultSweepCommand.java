package dev.erst.fingrind.contract.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;

/** Administrative command that sweeps one contiguous window ending at the selected date. */
public record InterimResultSweepCommand(LocalDate throughEffectiveDate) {
  /** Validates one interim-result-sweep command. */
  public InterimResultSweepCommand {
    Objects.requireNonNull(throughEffectiveDate, "throughEffectiveDate");
  }
}
