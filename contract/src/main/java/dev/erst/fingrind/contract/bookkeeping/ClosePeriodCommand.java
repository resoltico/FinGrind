package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ReportingPeriod;
import java.util.Objects;

/** Administrative command that closes one contiguous reporting period. */
public record ClosePeriodCommand(
    ReportingPeriod reportingPeriod, AccountCode closingEquityAccountCode) {
  /** Validates one close-period command. */
  public ClosePeriodCommand {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(closingEquityAccountCode, "closingEquityAccountCode");
  }
}
