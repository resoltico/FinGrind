package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link JournalEntry}. */
class JournalEntryTest {
  @Test
  void constructor_defensivelyCopiesLines() {
    List<JournalLine> lines = new ArrayList<>(balancedLines());
    JournalEntry journalEntry = new JournalEntry(LocalDate.parse("2026-04-07"), lines);

    lines.clear();

    assertEquals(2, journalEntry.lines().size());
  }

  @Test
  void constructor_rejectsEmptyLines() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new JournalEntry(LocalDate.parse("2026-04-07"), List.of()));
  }

  @Test
  void constructor_rejectsNullLinesAfterNormalizingThemToEmpty() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new JournalEntry(LocalDate.parse("2026-04-07"), null));
  }

  @Test
  void constructor_rejectsMixedCurrencyLines() {
    List<JournalLine> lines =
        List.of(
            new JournalLine(
                new AccountCode("1000"),
                JournalLine.EntrySide.DEBIT,
                new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
            new JournalLine(
                new AccountCode("2000"),
                JournalLine.EntrySide.CREDIT,
                new Money(new CurrencyCode("USD"), new BigDecimal("10.00"))));

    assertThrows(
        IllegalArgumentException.class,
        () -> new JournalEntry(LocalDate.parse("2026-04-07"), lines));
  }

  @Test
  void constructor_rejectsImbalancedLines() {
    List<JournalLine> lines =
        List.of(
            new JournalLine(
                new AccountCode("1000"),
                JournalLine.EntrySide.DEBIT,
                new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
            new JournalLine(
                new AccountCode("2000"),
                JournalLine.EntrySide.CREDIT,
                new Money(new CurrencyCode("EUR"), new BigDecimal("9.00"))));

    assertThrows(
        IllegalArgumentException.class,
        () -> new JournalEntry(LocalDate.parse("2026-04-07"), lines));
  }

  private static List<JournalLine> balancedLines() {
    return List.of(
        new JournalLine(
            new AccountCode("1000"),
            JournalLine.EntrySide.DEBIT,
            new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
        new JournalLine(
            new AccountCode("2000"),
            JournalLine.EntrySide.CREDIT,
            new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))));
  }
}
