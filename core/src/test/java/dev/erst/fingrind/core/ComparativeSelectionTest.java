package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Covers comparative-selection factories and report-surface compatibility guards. */
class ComparativeSelectionTest {
  @Test
  void factories_publishStableModesAndExplicitRanges() {
    ComparativeSelection none = ComparativeSelection.none();
    ComparativeSelection priorPeriod = ComparativeSelection.priorPeriod();
    LocalDate upperBound = LocalDate.parse("2026-06-30");
    ComparativeSelection explicitRange =
        ComparativeSelection.range(EffectiveDateRange.to(upperBound));

    assertSame(ComparativeSelection.None.INSTANCE, none);
    assertEquals(ComparativeMode.NONE, none.mode());
    assertSame(ComparativeSelection.PriorPeriod.INSTANCE, priorPeriod);
    assertEquals(ComparativeMode.PRIOR_PERIOD, priorPeriod.mode());
    assertEquals(ComparativeMode.RANGE, explicitRange.mode());
    assertEquals(
        EffectiveDateRange.to(upperBound),
        assertInstanceOf(ComparativeSelection.ExplicitRange.class, explicitRange)
            .effectiveDateRange());
  }

  @Test
  void explicitRange_rejectsNullEffectiveDateRange() {
    NullPointerException nullRangeFailure =
        assertThrows(NullPointerException.class, () -> ComparativeSelection.range(nullOf()));

    assertEquals("effectiveDateRange", nullRangeFailure.getMessage());
  }

  @Test
  void asOfCompatibility_acceptsNonComparativeAndUpperBoundOnlyRanges() {
    ComparativeSelection none = ComparativeSelection.none();
    ComparativeSelection priorPeriod = ComparativeSelection.priorPeriod();
    ComparativeSelection explicitRange =
        ComparativeSelection.range(EffectiveDateRange.to(LocalDate.parse("2026-06-30")));

    assertSame(none, ComparativeSelection.requireAsOfCompatible(none, "comparative"));
    assertSame(priorPeriod, ComparativeSelection.requireAsOfCompatible(priorPeriod, "comparative"));
    assertSame(
        explicitRange, ComparativeSelection.requireAsOfCompatible(explicitRange, "comparative"));
  }

  @Test
  void asOfCompatibility_rejectsNullInputsAndIncompatibleRanges() {
    NullPointerException nullSelectionFailure =
        assertThrows(
            NullPointerException.class,
            () -> ComparativeSelection.requireAsOfCompatible(nullOf(), "comparative"));
    NullPointerException nullFieldNameFailure =
        assertThrows(
            NullPointerException.class,
            () ->
                ComparativeSelection.requireAsOfCompatible(ComparativeSelection.none(), nullOf()));
    IllegalArgumentException unboundedRangeFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ComparativeSelection.requireAsOfCompatible(
                    ComparativeSelection.range(EffectiveDateRange.unbounded()), "comparative"));
    IllegalArgumentException lowerBoundRangeFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ComparativeSelection.requireAsOfCompatible(
                    ComparativeSelection.range(
                        EffectiveDateRange.from(LocalDate.parse("2026-06-01"))),
                    "comparative"));

    assertEquals("comparativeSelection", nullSelectionFailure.getMessage());
    assertEquals("fieldName", nullFieldNameFailure.getMessage());
    assertEquals(
        "comparative must use one upper-bound-only comparative range for as-of reports.",
        unboundedRangeFailure.getMessage());
    assertEquals(
        "comparative must use one upper-bound-only comparative range for as-of reports.",
        lowerBoundRangeFailure.getMessage());
  }

  @Test
  void boundedPeriodCompatibility_acceptsNonComparativeAndFullyBoundedRanges() {
    ComparativeSelection none = ComparativeSelection.none();
    ComparativeSelection priorPeriod = ComparativeSelection.priorPeriod();
    ComparativeSelection explicitRange =
        ComparativeSelection.range(
            EffectiveDateRange.bounded(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-06-30")));

    assertSame(none, ComparativeSelection.requireBoundedPeriodCompatible(none, "comparative"));
    assertSame(
        priorPeriod,
        ComparativeSelection.requireBoundedPeriodCompatible(priorPeriod, "comparative"));
    assertSame(
        explicitRange,
        ComparativeSelection.requireBoundedPeriodCompatible(explicitRange, "comparative"));
  }

  @Test
  void boundedPeriodCompatibility_rejectsNullInputsAndOpenRanges() {
    NullPointerException nullSelectionFailure =
        assertThrows(
            NullPointerException.class,
            () -> ComparativeSelection.requireBoundedPeriodCompatible(nullOf(), "comparative"));
    NullPointerException nullFieldNameFailure =
        assertThrows(
            NullPointerException.class,
            () ->
                ComparativeSelection.requireBoundedPeriodCompatible(
                    ComparativeSelection.none(), nullOf()));
    IllegalArgumentException upperBoundOnlyFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ComparativeSelection.requireBoundedPeriodCompatible(
                    ComparativeSelection.range(
                        EffectiveDateRange.to(LocalDate.parse("2026-06-30"))),
                    "comparative"));
    IllegalArgumentException lowerBoundOnlyFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ComparativeSelection.requireBoundedPeriodCompatible(
                    ComparativeSelection.range(
                        EffectiveDateRange.from(LocalDate.parse("2026-04-01"))),
                    "comparative"));

    assertEquals("comparativeSelection", nullSelectionFailure.getMessage());
    assertEquals("fieldName", nullFieldNameFailure.getMessage());
    assertEquals(
        "comparative must use one fully bounded comparative range for period reports.",
        upperBoundOnlyFailure.getMessage());
    assertEquals(
        "comparative must use one fully bounded comparative range for period reports.",
        lowerBoundOnlyFailure.getMessage());
  }
}
