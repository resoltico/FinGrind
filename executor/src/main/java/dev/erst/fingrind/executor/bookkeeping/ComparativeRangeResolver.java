package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.bookkeeping.policy.StatementComparativePolicy;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Resolves explicit comparative selections into concrete effective-date windows. */
public final class ComparativeRangeResolver {
  private ComparativeRangeResolver() {}

  /** Resolves one comparative window for an as-of report surface. */
  public static EffectiveDateRange asOf(
      BookIdentity bookIdentity,
      Optional<LocalDate> effectiveDateAsOf,
      ComparativeSelection comparativeSelection,
      StatementComparativePolicy comparativePolicy) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    Objects.requireNonNull(comparativeSelection, "comparativeSelection");
    Objects.requireNonNull(comparativePolicy, "comparativePolicy");
    return switch (comparativeSelection) {
      case ComparativeSelection.None _ -> EffectiveDateRange.unbounded();
      case ComparativeSelection.PriorPeriod _ ->
          comparativePolicy.comparativeAsOf(bookIdentity, effectiveDateAsOf);
      case ComparativeSelection.ExplicitRange explicitRange -> explicitRange.effectiveDateRange();
    };
  }

  /** Resolves one comparative window for a bounded-period report surface. */
  public static EffectiveDateRange period(
      BookIdentity bookIdentity,
      LocalDate effectiveDateFrom,
      LocalDate effectiveDateTo,
      ComparativeSelection comparativeSelection,
      StatementComparativePolicy comparativePolicy) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    Objects.requireNonNull(comparativeSelection, "comparativeSelection");
    Objects.requireNonNull(comparativePolicy, "comparativePolicy");
    return switch (comparativeSelection) {
      case ComparativeSelection.None _ -> EffectiveDateRange.unbounded();
      case ComparativeSelection.PriorPeriod _ ->
          comparativePolicy.comparativePeriod(bookIdentity, effectiveDateFrom, effectiveDateTo);
      case ComparativeSelection.ExplicitRange explicitRange -> explicitRange.effectiveDateRange();
    };
  }
}
