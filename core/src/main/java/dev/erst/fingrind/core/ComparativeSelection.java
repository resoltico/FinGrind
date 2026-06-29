package dev.erst.fingrind.core;

import java.util.Objects;

/** Typed comparative-selection contract shared by reporting queries and execution criteria. */
public sealed interface ComparativeSelection
    permits ComparativeSelection.None,
        ComparativeSelection.PriorPeriod,
        ComparativeSelection.ExplicitRange {
  /** Returns the advertised comparative mode for this selection. */
  ComparativeMode mode();

  /** Returns one selection that disables comparative output entirely. */
  static ComparativeSelection none() {
    return None.INSTANCE;
  }

  /** Returns one selection that derives the comparative window from the prior fiscal period. */
  static ComparativeSelection priorPeriod() {
    return PriorPeriod.INSTANCE;
  }

  /** Returns one selection that uses the supplied explicit effective-date range directly. */
  static ComparativeSelection range(EffectiveDateRange effectiveDateRange) {
    return new ExplicitRange(effectiveDateRange);
  }

  /** Validates that this selection is compatible with one as-of report surface. */
  static ComparativeSelection requireAsOfCompatible(
      ComparativeSelection comparativeSelection, String fieldName) {
    Objects.requireNonNull(comparativeSelection, "comparativeSelection");
    Objects.requireNonNull(fieldName, "fieldName");
    if (comparativeSelection instanceof ExplicitRange explicitRange) {
      EffectiveDateRange effectiveDateRange = explicitRange.effectiveDateRange();
      if (effectiveDateRange.effectiveDateFrom().isPresent()
          || effectiveDateRange.effectiveDateTo().isEmpty()) {
        throw new IllegalArgumentException(
            fieldName + " must use one upper-bound-only comparative range for as-of reports.");
      }
    }
    return comparativeSelection;
  }

  /** Validates that this selection is compatible with one bounded-period report surface. */
  static ComparativeSelection requireBoundedPeriodCompatible(
      ComparativeSelection comparativeSelection, String fieldName) {
    Objects.requireNonNull(comparativeSelection, "comparativeSelection");
    Objects.requireNonNull(fieldName, "fieldName");
    if (comparativeSelection instanceof ExplicitRange explicitRange) {
      EffectiveDateRange effectiveDateRange = explicitRange.effectiveDateRange();
      if (effectiveDateRange.effectiveDateFrom().isEmpty()
          || effectiveDateRange.effectiveDateTo().isEmpty()) {
        throw new IllegalArgumentException(
            fieldName + " must use one fully bounded comparative range for period reports.");
      }
    }
    return comparativeSelection;
  }

  /** Comparative disabled. */
  enum None implements ComparativeSelection {
    INSTANCE;

    @Override
    public ComparativeMode mode() {
      return ComparativeMode.NONE;
    }
  }

  /** Comparative derived from the prior fiscal period. */
  enum PriorPeriod implements ComparativeSelection {
    INSTANCE;

    @Override
    public ComparativeMode mode() {
      return ComparativeMode.PRIOR_PERIOD;
    }
  }

  /** Comparative taken directly from one explicit effective-date range. */
  record ExplicitRange(EffectiveDateRange effectiveDateRange) implements ComparativeSelection {
    public ExplicitRange {
      Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    }

    @Override
    public ComparativeMode mode() {
      return ComparativeMode.RANGE;
    }
  }
}
