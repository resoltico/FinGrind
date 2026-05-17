package dev.erst.fingrind.contract.operations;

import dev.erst.fingrind.core.BusinessEventKind;
import dev.erst.fingrind.core.CounterpartyId;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.util.Objects;
import java.util.Optional;

/** Paginated public query for persisted business events. */
public record ListBusinessEventsQuery(
    Optional<BusinessEventKind> businessEventKind,
    Optional<CounterpartyId> counterpartyId,
    EffectiveDateRange effectiveDateRange,
    int limit,
    Optional<BusinessEventPageCursor> cursor) {
  /** Validates one list-business-events query. */
  public ListBusinessEventsQuery {
    Objects.requireNonNull(businessEventKind, "businessEventKind");
    Objects.requireNonNull(counterpartyId, "counterpartyId");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    Objects.requireNonNull(cursor, "cursor");
    if (limit <= 0) {
      throw new IllegalArgumentException("Business event page limit must be positive.");
    }
  }
}
