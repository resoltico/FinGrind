package dev.erst.fingrind.contract.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;

/** Rejection for a posting attempt whose effective date predates the immutable book start. */
public record PostingEffectiveDateBeforeBookStart(
    LocalDate attemptedEffectiveDate, LocalDate bookStartEffectiveDate)
    implements FoundationalPostingRejection {
  public PostingEffectiveDateBeforeBookStart {
    Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
    Objects.requireNonNull(bookStartEffectiveDate, "bookStartEffectiveDate");
  }
}
