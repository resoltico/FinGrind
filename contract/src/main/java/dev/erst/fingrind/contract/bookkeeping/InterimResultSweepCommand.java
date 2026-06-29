package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.ReportingPeriod;
import java.util.Objects;

/** Administrative command that closes one contiguous reporting period. */
public record InterimResultSweepCommand(ReportingPeriod reportingPeriod) {
  /** Validates one interim-result-sweep command. */
  public InterimResultSweepCommand {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
  }
}
