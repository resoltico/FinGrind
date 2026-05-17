package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.core.ReportingPeriod;
import java.util.Objects;

/** Public query for one disclosure pack. */
public record DisclosurePackQuery(ReportingPeriod reportingPeriod) {
  /** Validates one disclosure-pack query. */
  public DisclosurePackQuery {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
  }
}
