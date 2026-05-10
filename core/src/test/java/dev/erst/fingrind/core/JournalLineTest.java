package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link JournalLine}. */
class JournalLineTest {
  @Test
  void constructor_acceptsPositiveAmount() {
    JournalLine journalLine =
        new JournalLine(
            new AccountCode("1000"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "12.50"));

    assertEquals(JournalLine.EntrySide.DEBIT, journalLine.side());
  }

  @Test
  void constructor_rejectsZeroAmount() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JournalLine(
                new AccountCode("1000"),
                JournalLine.EntrySide.DEBIT,
                Money.zero(CurrencyUnit.of("EUR"))));
  }
}
