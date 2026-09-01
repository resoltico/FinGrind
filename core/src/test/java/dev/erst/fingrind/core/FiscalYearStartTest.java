package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FiscalYearStart}. */
class FiscalYearStartTest {
  @Test
  void labeledFiscalYearBoundaries_followTheSelectedLabelAndClampLeapDays() {
    FiscalYearStart fiscalYearStart = new FiscalYearStart(2, 29);

    assertEquals(LocalDate.parse("2024-02-29"), fiscalYearStart.labeledFiscalYearStart(2024));
    assertEquals(LocalDate.parse("2025-02-28"), fiscalYearStart.labeledFiscalYearStart(2025));
    assertEquals(LocalDate.parse("2025-02-27"), fiscalYearStart.labeledFiscalYearEnd(2024));
    assertEquals(LocalDate.parse("2026-02-27"), fiscalYearStart.labeledFiscalYearEnd(2025));
  }

  @Test
  void containingBoundariesAndWireValue_followTheFiscalYearThatOwnsEachDate() {
    FiscalYearStart fiscalYearStart = FiscalYearStart.parse("04-01");

    assertEquals("04-01", fiscalYearStart.wireValue());
    assertEquals("04-01", fiscalYearStart.toString());
    assertEquals(LocalDate.of(2000, 4, 1), fiscalYearStart.monthDay().atYear(2000));
    assertEquals(
        LocalDate.of(2024, 4, 1),
        fiscalYearStart.containingFiscalYearStart(LocalDate.of(2024, 4, 1)));
    assertEquals(
        LocalDate.of(2023, 4, 1),
        fiscalYearStart.containingFiscalYearStart(LocalDate.of(2024, 3, 31)));
    assertEquals(
        LocalDate.of(2025, 3, 31),
        fiscalYearStart.containingFiscalYearEnd(LocalDate.of(2024, 4, 1)));
    assertEquals(
        LocalDate.of(2024, 3, 31),
        fiscalYearStart.containingFiscalYearEnd(LocalDate.of(2024, 3, 31)));
    assertTrue(
        fiscalYearStart.containsSingleFiscalYear(
            LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31)));
    assertFalse(
        fiscalYearStart.containsSingleFiscalYear(
            LocalDate.of(2025, 3, 31), LocalDate.of(2025, 4, 1)));
  }
}
