package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Focused branch coverage tests for {@link PdfValueFormatter}. */
class PdfValueFormatterTest {
  @Test
  void displayMoneyUsesCanonicalCurrencyScale() {
    assertEquals("12.50", PdfValueFormatter.displayMoney(Money.parse("EUR", "12.50")));
    assertEquals("42.00", PdfValueFormatter.displayMoney(Money.parse("EUR", "42.00")));
    assertEquals("100", PdfValueFormatter.displayMoney(Money.parse("JPY", "100")));
    assertEquals("1.250", PdfValueFormatter.displayMoney(Money.parse("BHD", "1.25")));
  }

  @Test
  void optionalDateFormatsNullAndConcreteDates() {
    assertEquals("(current)", PdfValueFormatter.optionalDate(null));
    assertEquals("2026-05-07", PdfValueFormatter.optionalDate(LocalDate.parse("2026-05-07")));
  }

  @Test
  void optionalDateRangeFormatsOpenAndBoundedRanges() {
    LocalDate from = LocalDate.parse("2026-05-01");
    LocalDate to = LocalDate.parse("2026-05-31");

    assertEquals("(start) to (current)", PdfValueFormatter.optionalDateRange(null, null));
    assertEquals("2026-05-01 to (current)", PdfValueFormatter.optionalDateRange(from, null));
    assertEquals("(start) to 2026-05-31", PdfValueFormatter.optionalDateRange(null, to));
    assertEquals("2026-05-01 to 2026-05-31", PdfValueFormatter.optionalDateRange(from, to));
  }

  @Test
  void effectiveDateRangeFormatsEveryStructuralVariant() {
    LocalDate from = LocalDate.parse("2026-05-01");
    LocalDate to = LocalDate.parse("2026-05-31");

    assertEquals(
        "(start) to (current)",
        PdfValueFormatter.effectiveDateRange(EffectiveDateRange.unbounded()));
    assertEquals(
        "2026-05-01 to (current)",
        PdfValueFormatter.effectiveDateRange(new EffectiveDateRange.From(from)));
    assertEquals(
        "(start) to 2026-05-31",
        PdfValueFormatter.effectiveDateRange(new EffectiveDateRange.To(to)));
    assertEquals(
        "2026-05-01 to 2026-05-31",
        PdfValueFormatter.effectiveDateRange(new EffectiveDateRange.Bounded(from, to)));
  }
}
