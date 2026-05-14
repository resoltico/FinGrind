package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping criteria for one as-of trial-balance view. */
public record TrialBalanceCriteria(
    Optional<LocalDate> effectiveDateTo, PostingCoverage postingCoverage) {
  public TrialBalanceCriteria {
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
  }
}
