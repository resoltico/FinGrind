package dev.erst.fingrind.contract;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Structurally typed effective-date filter shared by read models and ledger assertions. */
public sealed interface EffectiveDateRange
    permits EffectiveDateRange.Unbounded,
        EffectiveDateRange.From,
        EffectiveDateRange.To,
        EffectiveDateRange.Bounded {
  /** Returns the optional lower bound of this date range. */
  Optional<LocalDate> effectiveDateFrom();

  /** Returns the optional upper bound of this date range. */
  Optional<LocalDate> effectiveDateTo();

  /** Builds the canonical date range for the supplied optional bounds. */
  static EffectiveDateRange of(
      Optional<LocalDate> effectiveDateFrom, Optional<LocalDate> effectiveDateTo) {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isPresent() && effectiveDateTo.isPresent()) {
      return new Bounded(effectiveDateFrom.orElseThrow(), effectiveDateTo.orElseThrow());
    }
    if (effectiveDateFrom.isPresent()) {
      return new From(effectiveDateFrom.orElseThrow());
    }
    if (effectiveDateTo.isPresent()) {
      return new To(effectiveDateTo.orElseThrow());
    }
    return Unbounded.INSTANCE;
  }

  /** Returns one unbounded range with no lower or upper effective-date filter. */
  static EffectiveDateRange unbounded() {
    return Unbounded.INSTANCE;
  }

  /** Returns whether this range admits the supplied effective date. */
  default boolean contains(LocalDate effectiveDate) {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    return effectiveDateFrom().stream().allMatch(date -> !effectiveDate.isBefore(date))
        && effectiveDateTo().stream().allMatch(date -> !effectiveDate.isAfter(date));
  }

  /** Unbounded date range with no effective-date filters. */
  enum Unbounded implements EffectiveDateRange {
    INSTANCE;

    @Override
    public Optional<LocalDate> effectiveDateFrom() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> effectiveDateTo() {
      return Optional.empty();
    }
  }

  /** Date range bounded only by a lower effective date. */
  record From(LocalDate lowerBound) implements EffectiveDateRange {
    /** Validates the lower-bound-only date range. */
    public From {
      Objects.requireNonNull(lowerBound, "lowerBound");
    }

    @Override
    public Optional<LocalDate> effectiveDateFrom() {
      return Optional.of(lowerBound);
    }

    @Override
    public Optional<LocalDate> effectiveDateTo() {
      return Optional.empty();
    }
  }

  /** Date range bounded only by an upper effective date. */
  record To(LocalDate upperBound) implements EffectiveDateRange {
    /** Validates the upper-bound-only date range. */
    public To {
      Objects.requireNonNull(upperBound, "upperBound");
    }

    @Override
    public Optional<LocalDate> effectiveDateFrom() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> effectiveDateTo() {
      return Optional.of(upperBound);
    }
  }

  /** Date range bounded by both lower and upper effective dates. */
  record Bounded(LocalDate lowerBound, LocalDate upperBound) implements EffectiveDateRange {
    /** Validates the fully bounded date range. */
    public Bounded {
      Objects.requireNonNull(lowerBound, "lowerBound");
      Objects.requireNonNull(upperBound, "upperBound");
      if (lowerBound.isAfter(upperBound)) {
        throw new IllegalArgumentException(
            "effectiveDateFrom must be on or before effectiveDateTo.");
      }
    }

    @Override
    public Optional<LocalDate> effectiveDateFrom() {
      return Optional.of(lowerBound);
    }

    @Override
    public Optional<LocalDate> effectiveDateTo() {
      return Optional.of(upperBound);
    }
  }

  /** Returns every stable structural variant name for tests and contract discovery. */
  static List<String> variantNames() {
    return List.of("unbounded", "from", "to", "bounded");
  }
}
