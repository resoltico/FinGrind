package dev.erst.fingrind.core.attestation;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Applies calendar-boundary arithmetic while retaining the failing date calculation as evidence.
 */
final class AttestationPeriodCloseCalendar {
  private AttestationPeriodCloseCalendar() {}

  static LocalDate nextDay(LocalDate date, AttestationAuthorizationFailure failure) {
    try {
      return date.plusDays(1);
    } catch (DateTimeException exception) {
      throw new AttestationAuthorizationException(failure, exception);
    }
  }

  static LocalDate previousDay(LocalDate date, AttestationAuthorizationFailure failure) {
    try {
      return date.minusDays(1);
    } catch (DateTimeException exception) {
      throw new AttestationAuthorizationException(failure, exception);
    }
  }
}
