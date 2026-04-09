package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link JournalLine}. */
class JournalLineTest {
  @Test
  void constructor_acceptsPositiveAmount() {
    JournalLine journalLine =
        new JournalLine(
            new AccountCode("1000"),
            JournalLine.EntrySide.DEBIT,
            new Money(new CurrencyCode("EUR"), new BigDecimal("12.50")));

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
                new Money(new CurrencyCode("EUR"), BigDecimal.ZERO)));
  }
}
