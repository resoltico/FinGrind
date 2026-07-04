package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link EffectiveDateHorizonPolicy}. */
class EffectiveDateHorizonPolicyTest {
  @Test
  void requireNotAfterToday_acceptsPastAndCurrentUtcDates() {
    Clock clock = Clock.fixed(Instant.parse("2026-05-01T00:30:00Z"), ZoneId.of("Pacific/Honolulu"));

    assertDoesNotThrow(
        () ->
            EffectiveDateHorizonPolicy.requireNotAfterToday(LocalDate.parse("2026-04-30"), clock));
    assertDoesNotThrow(
        () ->
            EffectiveDateHorizonPolicy.requireNotAfterToday(LocalDate.parse("2026-05-01"), clock));
  }

  @Test
  void requireNotAfterToday_rejectsFutureDatesUsingUtcCalendarDays() {
    Clock clock = Clock.fixed(Instant.parse("2026-05-01T23:30:00Z"), ZoneId.of("Europe/Riga"));

    EffectiveDateHorizonPolicy.FutureEffectiveDateException exception =
        assertThrows(
            EffectiveDateHorizonPolicy.FutureEffectiveDateException.class,
            () ->
                EffectiveDateHorizonPolicy.requireNotAfterToday(
                    LocalDate.parse("2026-05-02"), clock));

    assertEquals(LocalDate.parse("2026-05-02"), exception.attemptedEffectiveDate());
    assertEquals(LocalDate.parse("2026-05-01"), exception.currentUtcDate());
    assertEquals(
        "Effective date '2026-05-02' must not fall after current UTC date '2026-05-01'.",
        exception.getMessage());
  }

  @Test
  void futureEffectiveDateException_validatesInputsAndPublishesAccessors() {
    EffectiveDateHorizonPolicy.FutureEffectiveDateException exception =
        new EffectiveDateHorizonPolicy.FutureEffectiveDateException(
            LocalDate.parse("2026-05-03"), LocalDate.parse("2026-05-01"));

    assertEquals(LocalDate.parse("2026-05-03"), exception.attemptedEffectiveDate());
    assertEquals(LocalDate.parse("2026-05-01"), exception.currentUtcDate());
    assertThrows(
        NullPointerException.class,
        () ->
            new EffectiveDateHorizonPolicy.FutureEffectiveDateException(
                nullOf(), LocalDate.parse("2026-05-01")));
    assertThrows(
        NullPointerException.class,
        () ->
            new EffectiveDateHorizonPolicy.FutureEffectiveDateException(
                LocalDate.parse("2026-05-03"), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> EffectiveDateHorizonPolicy.requireNotAfterToday(nullOf(), Clock.systemUTC()));
    assertThrows(
        NullPointerException.class,
        () ->
            EffectiveDateHorizonPolicy.requireNotAfterToday(
                LocalDate.parse("2026-05-01"), nullOf()));
  }
}
