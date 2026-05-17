package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import java.util.List;
import java.util.Objects;

/** Public disclosure pack for one reporting period. */
public record DisclosurePack(
    BookIdentity bookIdentity, ReportingPeriod reportingPeriod, List<DisclosureNote> notes) {
  /** Defensively copies one disclosure pack. */
  public DisclosurePack {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
  }
}
