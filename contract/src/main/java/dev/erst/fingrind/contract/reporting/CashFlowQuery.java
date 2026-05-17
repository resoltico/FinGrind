package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.core.ReportingPeriod;
import java.util.Objects;

/** Public query for one statement of cash flows. */
public record CashFlowQuery(ReportingPeriod reportingPeriod) {
  /** Validates one cash-flow query. */
  public CashFlowQuery {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
  }
}
