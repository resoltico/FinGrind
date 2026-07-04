package dev.erst.fingrind.core;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/** Shared UTC effective-date horizon policy for write and close workflows. */
public final class EffectiveDateHorizonPolicy {
  private EffectiveDateHorizonPolicy() {}

  /** Requires one effective date to fall on or before the current UTC date. */
  public static void requireNotAfterToday(LocalDate effectiveDate, Clock clock) {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    LocalDate currentUtcDate = currentUtcDate(clock);
    if (effectiveDate.isAfter(currentUtcDate)) {
      throw new FutureEffectiveDateException(effectiveDate, currentUtcDate);
    }
  }

  private static LocalDate currentUtcDate(Clock clock) {
    return Objects.requireNonNull(clock, "clock").instant().atZone(ZoneOffset.UTC).toLocalDate();
  }

  /** Raised when one requested effective date falls after the current UTC date. */
  public static final class FutureEffectiveDateException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final LocalDate attemptedEffectiveDate;
    private final LocalDate currentUtcDate;

    /** Creates one horizon violation with both the attempted and current UTC dates. */
    public FutureEffectiveDateException(
        LocalDate attemptedEffectiveDate, LocalDate currentUtcDate) {
      super(
          "Effective date '%s' must not fall after current UTC date '%s'."
              .formatted(
                  Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate"),
                  Objects.requireNonNull(currentUtcDate, "currentUtcDate")));
      this.attemptedEffectiveDate = attemptedEffectiveDate;
      this.currentUtcDate = currentUtcDate;
    }

    /** Returns the rejected effective date supplied by the caller. */
    public LocalDate attemptedEffectiveDate() {
      return attemptedEffectiveDate;
    }

    /** Returns the current UTC date resolved from the application clock. */
    public LocalDate currentUtcDate() {
      return currentUtcDate;
    }
  }
}
