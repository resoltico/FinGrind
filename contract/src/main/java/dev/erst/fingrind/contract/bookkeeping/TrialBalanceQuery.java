package dev.erst.fingrind.contract.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** As-of query for a book-wide trial balance report. */
public record TrialBalanceQuery(Optional<LocalDate> effectiveDateTo) {
  /** Validates one trial-balance query. */
  public TrialBalanceQuery {
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
  }
}
