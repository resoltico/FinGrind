package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** As-of query for a book-wide trial balance report. */
public record TrialBalanceQuery(
    Optional<LocalDate> effectiveDateAsOf, PostingCoverage postingCoverage) {
  /** Validates one trial-balance query. */
  public TrialBalanceQuery {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
  }
}
