package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.EffectiveDateRange;
import java.util.Objects;
import java.util.Optional;

/** Normalizes bounded-period comparative windows before statement snapshot computation. */
final class PeriodComparativeRangeSupport {
  private PeriodComparativeRangeSupport() {}

  static Optional<EffectiveDateRange> boundedRange(EffectiveDateRange comparativeRange) {
    Objects.requireNonNull(comparativeRange, "comparativeRange");
    if (comparativeRange.effectiveDateFrom().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        EffectiveDateRange.of(
            comparativeRange.effectiveDateFrom().orElseThrow(),
            comparativeRange
                .effectiveDateTo()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Bounded-period comparative ranges must include effectiveDateTo."))));
  }
}
