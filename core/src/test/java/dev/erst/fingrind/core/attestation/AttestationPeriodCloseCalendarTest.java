package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DateTimeException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Pins close-calendar arithmetic and the preserved cause at the date domain limits. */
class AttestationPeriodCloseCalendarTest {
  private static final AttestationAuthorizationFailure FAILURE =
      AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID;

  @Test
  void movesOneCalendarDayInEitherDirectionAndPreservesBoundaryCauses() {
    assertEquals(
        LocalDate.of(2026, 1, 1),
        AttestationPeriodCloseCalendar.nextDay(LocalDate.of(2025, 12, 31), FAILURE));
    assertEquals(
        LocalDate.of(2025, 12, 31),
        AttestationPeriodCloseCalendar.previousDay(LocalDate.of(2026, 1, 1), FAILURE));

    AttestationAuthorizationException nextFailure =
        assertThrows(
            AttestationAuthorizationException.class,
            () -> AttestationPeriodCloseCalendar.nextDay(LocalDate.MAX, FAILURE));
    AttestationAuthorizationException previousFailure =
        assertThrows(
            AttestationAuthorizationException.class,
            () -> AttestationPeriodCloseCalendar.previousDay(LocalDate.MIN, FAILURE));

    assertEquals(FAILURE, nextFailure.failure());
    assertInstanceOf(DateTimeException.class, nextFailure.getCause());
    assertEquals(FAILURE, previousFailure.failure());
    assertInstanceOf(DateTimeException.class, previousFailure.getCause());
  }
}
