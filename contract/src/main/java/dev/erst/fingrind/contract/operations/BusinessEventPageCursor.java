package dev.erst.fingrind.contract.operations;

import dev.erst.fingrind.core.BusinessEventId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Stable keyset cursor for reverse-chronological business-event pagination. */
public record BusinessEventPageCursor(
    LocalDate effectiveDate, Instant recordedAt, BusinessEventId businessEventId) {
  /** Validates one business-event page cursor. */
  public BusinessEventPageCursor {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(businessEventId, "businessEventId");
  }
}
