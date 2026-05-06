package dev.erst.fingrind.executor.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping criteria for one as-of trial-balance view. */
public record TrialBalanceCriteria(Optional<LocalDate> effectiveDateTo) {
  public TrialBalanceCriteria {
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
  }
}
