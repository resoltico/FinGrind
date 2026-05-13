package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Covers the inclusive reporting-period value object used by close and statement workflows. */
class ReportingPeriodTest {
  @Test
  void constructorRejectsInvalidOrderAndNulls() {
    NullPointerException fromFailure =
        assertThrows(
            NullPointerException.class,
            () -> new ReportingPeriod(nullOf(), LocalDate.parse("2026-04-07")));
    assertEquals("effectiveDateFrom", fromFailure.getMessage());

    NullPointerException toFailure =
        assertThrows(
            NullPointerException.class,
            () -> new ReportingPeriod(LocalDate.parse("2026-04-01"), nullOf()));
    assertEquals("effectiveDateTo", toFailure.getMessage());

    IllegalArgumentException orderFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ReportingPeriod(LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-07")));
    assertEquals(
        "effectiveDateFrom must be on or before effectiveDateTo.", orderFailure.getMessage());
  }

  @Test
  void effectiveDateRangeContainsBoundariesAndDayAfterFollowInclusiveRules() {
    ReportingPeriod period =
        new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-07"));

    assertEquals(
        EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-07")),
        period.effectiveDateRange());
    assertTrue(period.contains(LocalDate.parse("2026-04-01")));
    assertTrue(period.contains(LocalDate.parse("2026-04-07")));
    assertFalse(period.contains(LocalDate.parse("2026-03-31")));
    assertFalse(period.contains(LocalDate.parse("2026-04-08")));
    assertEquals(LocalDate.parse("2026-04-08"), period.dayAfter());

    NullPointerException containsFailure =
        assertThrows(NullPointerException.class, () -> period.contains(nullOf()));
    assertEquals("effectiveDate", containsFailure.getMessage());
  }
}
