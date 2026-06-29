package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Shared validation and immutable-list support for comparative bookkeeping statement views. */
final class BookkeepingStatementViewValidation {
  private BookkeepingStatementViewValidation() {}

  static void requireComparativeStatementHeader(
      BookIdentity bookIdentity,
      LocalDate effectiveDateFrom,
      LocalDate effectiveDateTo,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    Objects.requireNonNull(comparativeEffectiveDateRange, "comparativeEffectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
  }

  static <T> List<T> immutableList(String fieldName, List<T> values) {
    return List.copyOf(Objects.requireNonNull(values, fieldName));
  }
}
