package dev.erst.fingrind.executor.bookkeeping.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for fiscal-year anchored comparative date derivation rules. */
class FiscalYearAnchoredStatementComparativePolicyTest {
  private static final BookIdentity FEBRUARY_YEAR_BOOK =
      new BookIdentity(
          new EntityProfile(new BookEntityName("Leap Shop"), java.util.List.of()),
          dev.erst.fingrind.core.AccountingKernelProfiles.COUNTRY_AGNOSTIC_BOOKKEEPING_KERNEL,
          CurrencyUnit.of("EUR"),
          FiscalYearStart.parse("02-29"));
  private static final BookIdentity CALENDAR_YEAR_BOOK =
      new BookIdentity(
          new EntityProfile(new BookEntityName("Calendar Shop"), java.util.List.of()),
          dev.erst.fingrind.core.AccountingKernelProfiles.COUNTRY_AGNOSTIC_BOOKKEEPING_KERNEL,
          CurrencyUnit.of("EUR"),
          FiscalYearStart.parse("01-01"));

  @Test
  void comparativePeriod_rejectsInvertedDateRange() {
    FiscalYearAnchoredStatementComparativePolicy policy =
        new FiscalYearAnchoredStatementComparativePolicy();

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                policy.comparativePeriod(
                    FEBRUARY_YEAR_BOOK,
                    LocalDate.parse("2026-05-02"),
                    LocalDate.parse("2026-05-01")));

    assertEquals("effectiveDateFrom must be on or before effectiveDateTo.", failure.getMessage());
  }

  @Test
  void comparativeAsOf_clampsToPreviousFiscalYearEndWhenOffsetOverflows() {
    FiscalYearAnchoredStatementComparativePolicy policy =
        new FiscalYearAnchoredStatementComparativePolicy();

    assertEquals(
        EffectiveDateRange.of(null, LocalDate.parse("2023-02-28")),
        policy.comparativeAsOf(FEBRUARY_YEAR_BOOK, Optional.of(LocalDate.parse("2024-02-29"))));
  }

  @Test
  void comparativeAsOf_keepsEquivalentOffsetWhenItFitsInsidePreviousFiscalYear() {
    FiscalYearAnchoredStatementComparativePolicy policy =
        new FiscalYearAnchoredStatementComparativePolicy();

    assertEquals(
        EffectiveDateRange.of(null, LocalDate.parse("2025-05-14")),
        policy.comparativeAsOf(CALENDAR_YEAR_BOOK, Optional.of(LocalDate.parse("2026-05-14"))));
  }
}
