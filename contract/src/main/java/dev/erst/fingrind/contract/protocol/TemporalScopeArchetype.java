package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical temporal-scope archetypes shared by query, report, and administrative commands. */
public enum TemporalScopeArchetype {
  RANGED_FILTER,
  BOUNDED_PERIOD,
  THROUGH_DATE,
  FISCAL_YEAR_LABEL,
  AS_OF_DATE;

  /** Returns the stable wire value for this temporal-scope archetype. */
  public String wireValue() {
    return switch (this) {
      case RANGED_FILTER -> "ranged-filter";
      case BOUNDED_PERIOD -> "bounded-period";
      case THROUGH_DATE -> "through-date";
      case FISCAL_YEAR_LABEL -> "fiscal-year-label";
      case AS_OF_DATE -> "as-of-date";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return List.of(
        RANGED_FILTER.wireValue(),
        BOUNDED_PERIOD.wireValue(),
        THROUGH_DATE.wireValue(),
        FISCAL_YEAR_LABEL.wireValue(),
        AS_OF_DATE.wireValue());
  }
}
