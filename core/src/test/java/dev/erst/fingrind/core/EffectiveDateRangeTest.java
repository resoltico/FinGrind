package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link EffectiveDateRange}. */
class EffectiveDateRangeTest {
  @Test
  void factories_buildEveryStructuralVariant() {
    EffectiveDateRange unbounded = EffectiveDateRange.of(null, null);
    EffectiveDateRange from = EffectiveDateRange.of(LocalDate.parse("2026-04-01"), null);
    EffectiveDateRange to = EffectiveDateRange.of(null, LocalDate.parse("2026-04-30"));
    EffectiveDateRange bounded =
        EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));

    assertInstanceOf(EffectiveDateRange.Unbounded.class, unbounded);
    assertInstanceOf(EffectiveDateRange.From.class, from);
    assertInstanceOf(EffectiveDateRange.To.class, to);
    assertInstanceOf(EffectiveDateRange.Bounded.class, bounded);
    assertEquals(Optional.of(LocalDate.parse("2026-04-01")), from.effectiveDateFrom());
    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), to.effectiveDateTo());
    assertEquals(Optional.of(LocalDate.parse("2026-04-01")), bounded.effectiveDateFrom());
    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), bounded.effectiveDateTo());
  }

  @Test
  void unboundedFactory_reusesSingleton() {
    assertEquals(EffectiveDateRange.Unbounded.INSTANCE, EffectiveDateRange.unbounded());
  }

  @Test
  void contains_respectsLowerAndUpperBounds() {
    EffectiveDateRange unbounded = EffectiveDateRange.unbounded();
    EffectiveDateRange from = EffectiveDateRange.of(LocalDate.parse("2026-04-01"), null);
    EffectiveDateRange to = EffectiveDateRange.of(null, LocalDate.parse("2026-04-30"));
    EffectiveDateRange bounded =
        EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));

    assertTrue(unbounded.contains(LocalDate.parse("2026-04-15")));
    assertFalse(from.contains(LocalDate.parse("2026-03-31")));
    assertTrue(from.contains(LocalDate.parse("2026-04-01")));
    assertTrue(to.contains(LocalDate.parse("2026-04-30")));
    assertFalse(to.contains(LocalDate.parse("2026-05-01")));
    assertTrue(bounded.contains(LocalDate.parse("2026-04-15")));
    assertFalse(bounded.contains(LocalDate.parse("2026-05-01")));
  }

  @Test
  void constructors_rejectInvalidBounds() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new EffectiveDateRange.Bounded(
                    LocalDate.parse("2026-05-01"), LocalDate.parse("2026-04-30")));

    assertEquals("effectiveDateFrom must be on or before effectiveDateTo.", exception.getMessage());
  }

  @Test
  void variantNames_returnsStableOrder() {
    assertEquals(List.of("unbounded", "from", "to", "bounded"), EffectiveDateRange.variantNames());
  }
}
