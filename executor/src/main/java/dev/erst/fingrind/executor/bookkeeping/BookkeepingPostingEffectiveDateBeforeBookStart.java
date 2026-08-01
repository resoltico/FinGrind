package dev.erst.fingrind.executor.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;

/** Refusal for a posting attempt whose effective date predates the immutable book start. */
public record BookkeepingPostingEffectiveDateBeforeBookStart(
    LocalDate attemptedEffectiveDate, LocalDate bookStartEffectiveDate)
    implements FoundationalBookkeepingPostingRejection {
  public BookkeepingPostingEffectiveDateBeforeBookStart {
    Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
    Objects.requireNonNull(bookStartEffectiveDate, "bookStartEffectiveDate");
  }
}
