package dev.erst.fingrind.contract.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Point-in-time request for every durable accrual cut-off and its remaining lifecycle amount. */
public record AccrualCutoffScheduleQuery(Optional<LocalDate> effectiveDateAsOf) {
  /** Validates one accrual cut-off schedule request. */
  public AccrualCutoffScheduleQuery {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
  }
}
