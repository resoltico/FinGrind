package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.core.ReportingPeriod;
import java.util.Objects;

/** Public query for one statement of comprehensive income. */
public record ComprehensiveIncomeQuery(ReportingPeriod reportingPeriod) {
  /** Validates one comprehensive-income query. */
  public ComprehensiveIncomeQuery {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
  }
}
