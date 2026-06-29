package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.bookkeeping.policy.StatementComparativePolicy;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for explicit comparative-range resolution on executor-side report criteria. */
class ComparativeRangeResolverTest {
  private static final LocalDate AS_OF_DATE = LocalDate.parse("2026-04-30");
  private static final LocalDate PERIOD_FROM = LocalDate.parse("2026-04-01");
  private static final LocalDate PERIOD_TO = LocalDate.parse("2026-04-30");
  private static final EffectiveDateRange PRIOR_AS_OF_RANGE =
      EffectiveDateRange.to(LocalDate.parse("2025-04-30"));
  private static final EffectiveDateRange PRIOR_PERIOD_RANGE =
      EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30"));
  private static final StatementComparativePolicy COMPARATIVE_POLICY =
      new FixedComparativePolicy(PRIOR_AS_OF_RANGE, PRIOR_PERIOD_RANGE);

  @Test
  void asOf_resolves_none_prior_period_and_explicit_range() {
    EffectiveDateRange explicitRange = EffectiveDateRange.to(LocalDate.parse("2024-12-31"));

    assertEquals(
        EffectiveDateRange.unbounded(),
        ComparativeRangeResolver.asOf(
            bookIdentity(),
            Optional.of(AS_OF_DATE),
            ComparativeSelection.none(),
            COMPARATIVE_POLICY));
    assertEquals(
        PRIOR_AS_OF_RANGE,
        ComparativeRangeResolver.asOf(
            bookIdentity(),
            Optional.of(AS_OF_DATE),
            ComparativeSelection.priorPeriod(),
            COMPARATIVE_POLICY));
    assertEquals(
        explicitRange,
        ComparativeRangeResolver.asOf(
            bookIdentity(),
            Optional.of(AS_OF_DATE),
            ComparativeSelection.range(explicitRange),
            COMPARATIVE_POLICY));
  }

  @Test
  void period_resolves_none_prior_period_and_explicit_range() {
    EffectiveDateRange explicitRange =
        EffectiveDateRange.of(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-31"));

    assertEquals(
        EffectiveDateRange.unbounded(),
        ComparativeRangeResolver.period(
            bookIdentity(),
            PERIOD_FROM,
            PERIOD_TO,
            ComparativeSelection.none(),
            COMPARATIVE_POLICY));
    assertEquals(
        PRIOR_PERIOD_RANGE,
        ComparativeRangeResolver.period(
            bookIdentity(),
            PERIOD_FROM,
            PERIOD_TO,
            ComparativeSelection.priorPeriod(),
            COMPARATIVE_POLICY));
    assertEquals(
        explicitRange,
        ComparativeRangeResolver.period(
            bookIdentity(),
            PERIOD_FROM,
            PERIOD_TO,
            ComparativeSelection.range(explicitRange),
            COMPARATIVE_POLICY));
  }

  private record FixedComparativePolicy(
      EffectiveDateRange priorAsOfRange, EffectiveDateRange priorPeriodRange)
      implements StatementComparativePolicy {
    @Override
    public EffectiveDateRange comparativeAsOf(
        dev.erst.fingrind.core.BookIdentity bookIdentity, Optional<LocalDate> effectiveDateTo) {
      return priorAsOfRange;
    }

    @Override
    public EffectiveDateRange comparativePeriod(
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        LocalDate effectiveDateFrom,
        LocalDate effectiveDateTo) {
      return priorPeriodRange;
    }
  }
}
