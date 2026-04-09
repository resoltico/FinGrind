package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link JournalEntry}. */
class JournalEntryTest {
  @Test
  void constructor_defensivelyCopiesLinesAndKeepsCorrectionReference() {
    List<JournalLine> lines = new ArrayList<>(balancedLines());
    JournalEntry journalEntry =
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            lines,
            Optional.of(
                new CorrectionReference(
                    CorrectionReference.CorrectionKind.AMENDMENT, new PostingId("posting-1"))));

    lines.clear();

    assertEquals(2, journalEntry.lines().size());
    assertEquals(
        Optional.of(
            new CorrectionReference(
                CorrectionReference.CorrectionKind.AMENDMENT, new PostingId("posting-1"))),
        journalEntry.correctionReference());
  }

  @Test
  void constructor_rejectsEmptyLines() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new JournalEntry(LocalDate.parse("2026-04-07"), List.of(), Optional.empty()));
  }

  @Test
  void constructor_rejectsNullLinesAfterNormalizingThemToEmpty() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new JournalEntry(LocalDate.parse("2026-04-07"), null, Optional.empty()));
  }

  @Test
  void constructor_defaultsNullCorrectionReferenceToEmpty() {
    JournalEntry journalEntry =
        new JournalEntry(LocalDate.parse("2026-04-07"), balancedLines(), null);

    assertEquals(Optional.empty(), journalEntry.correctionReference());
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
        () -> new JournalEntry(LocalDate.parse("2026-04-07"), lines, Optional.empty()));
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
        () -> new JournalEntry(LocalDate.parse("2026-04-07"), lines, Optional.empty()));
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
