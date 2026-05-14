package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FiscalYearStart;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Derives comparative windows from the book's fiscal-year anchor instead of blunt calendar-year
 * subtraction.
 */
@NullMarked
final class FiscalYearAnchoredStatementComparativePolicy implements StatementComparativePolicy {
  @Override
  public EffectiveDateRange comparativeAsOf(
      BookIdentity bookIdentity, Optional<LocalDate> effectiveDateTo) {
    StatementComparativePolicy.requireBookIdentity(bookIdentity);
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    return EffectiveDateRange.of(
        null,
        effectiveDateTo
            .map(date -> comparativeEquivalent(bookIdentity.fiscalYearStart(), date))
            .orElse(null));
  }

  @Override
  public EffectiveDateRange comparativePeriod(
      BookIdentity bookIdentity, LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
    StatementComparativePolicy.requireBookIdentity(bookIdentity);
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    FiscalYearStart fiscalYearStart = bookIdentity.fiscalYearStart();
    return EffectiveDateRange.of(
        comparativeEquivalent(fiscalYearStart, effectiveDateFrom),
        comparativeEquivalent(fiscalYearStart, effectiveDateTo));
  }

  private static LocalDate comparativeEquivalent(FiscalYearStart fiscalYearStart, LocalDate date) {
    LocalDate currentFiscalYearStart = fiscalYearStart.containingFiscalYearStart(date);
    LocalDate previousFiscalYearEnd = currentFiscalYearStart.minusDays(1);
    LocalDate previousFiscalYearStart =
        fiscalYearStart.containingFiscalYearStart(previousFiscalYearEnd);
    long offsetDays = ChronoUnit.DAYS.between(currentFiscalYearStart, date);
    LocalDate comparativeDate = previousFiscalYearStart.plusDays(offsetDays);
    return List.of(comparativeDate, previousFiscalYearEnd).stream()
        .min(LocalDate::compareTo)
        .orElseThrow();
  }
}
