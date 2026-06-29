package dev.erst.fingrind.executor.bookkeeping.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for bounded-period comparative range normalization invariants. */
class PeriodComparativeRangeSupportTest {
  @Test
  void boundedRange_returns_empty_for_unbounded_ranges_and_preserves_bounded_ranges() {
    EffectiveDateRange boundedRange =
        EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30"));

    assertEquals(
        Optional.empty(),
        PeriodComparativeRangeSupport.boundedRange(EffectiveDateRange.unbounded()));
    assertEquals(
        Optional.of(boundedRange), PeriodComparativeRangeSupport.boundedRange(boundedRange));
  }

  @Test
  void boundedRange_rejects_one_sided_lower_bound_ranges() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                PeriodComparativeRangeSupport.boundedRange(
                    EffectiveDateRange.of(LocalDate.parse("2025-04-01"), null)));

    assertEquals(
        "Bounded-period comparative ranges must include effectiveDateTo.", failure.getMessage());
  }
}
