package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.ReportingPeriod;
import java.util.Objects;

/** Administrative command that closes one fiscal year. */
public record FiscalYearCloseCommand(ReportingPeriod reportingPeriod) {
  /** Validates one fiscal-year-close command. */
  public FiscalYearCloseCommand {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
  }
}
