package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.ReportingPeriod;
import java.util.Objects;

/** Administrative command that closes one contiguous reporting period. */
public record PeriodResultTransferCommand(ReportingPeriod reportingPeriod) {
  /** Validates one transfer-period-result command. */
  public PeriodResultTransferCommand {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
  }
}
