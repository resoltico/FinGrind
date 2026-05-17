package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.ReportingPeriod;
import java.util.List;
import java.util.Objects;

/** Public statement-of-cash-flows projection in one functional currency. */
public record CashFlowReport(
    BookIdentity bookIdentity,
    ReportingPeriod reportingPeriod,
    List<CashFlowLine> lines,
    Money netChangeInCash) {
  /** Defensively copies and validates one cash-flow report. */
  public CashFlowReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    Objects.requireNonNull(netChangeInCash, "netChangeInCash");
  }
}
