package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
