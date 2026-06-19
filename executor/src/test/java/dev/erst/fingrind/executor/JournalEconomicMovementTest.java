package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for the per-account netting helper behind raw-journal economic-nullity. */
class JournalEconomicMovementTest {
  @Test
  void isEconomicallyNull_returnsTrueWhenEveryAccountNetsToZero() {
    assertTrue(
        JournalEconomicMovement.isEconomicallyNull(
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "10.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("1000", JournalLine.EntrySide.CREDIT, "10.00"))));
  }

  @Test
  void isEconomicallyNull_returnsFalseWhenTheFinalReducedAccountKeepsMovement() {
    assertFalse(
        JournalEconomicMovement.isEconomicallyNull(
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("1000", JournalLine.EntrySide.CREDIT, "10.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "10.00"))));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }
}
