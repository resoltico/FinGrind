package dev.erst.fingrind.contract.operations;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Stable page of business-event records. */
public record BusinessEventPage(
    List<BusinessEventRecord> businessEvents,
    int limit,
    Optional<BusinessEventPageCursor> nextCursor) {
  /** Defensively copies one business-event page. */
  public BusinessEventPage {
    businessEvents = List.copyOf(Objects.requireNonNull(businessEvents, "businessEvents"));
    Objects.requireNonNull(nextCursor, "nextCursor");
    if (limit <= 0) {
      throw new IllegalArgumentException("Business event page limit must be positive.");
    }
  }
}
