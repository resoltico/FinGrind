package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Focused branch coverage tests for {@link PdfValueFormatter}. */
class PdfValueFormatterTest {
  @Test
  void displayMoneyAndAmountStripTrailingZeros() {
    assertEquals("12.5", PdfValueFormatter.displayAmount(new BigDecimal("12.500")));
    assertEquals(
        "42",
        PdfValueFormatter.displayMoney(
            new Money(new CurrencyCode("EUR"), new BigDecimal("42.0000"))));
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
