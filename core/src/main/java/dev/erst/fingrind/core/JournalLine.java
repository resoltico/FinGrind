package dev.erst.fingrind.core;

import java.util.Objects;

/** One debit or credit line in a journal entry. */
public record JournalLine(AccountCode accountCode, EntrySide side, PositiveMoney amount) {
  /** Side of the journal equation carried by one line. */
  public enum EntrySide {
    DEBIT,
    CREDIT
  }

  /** Convenience overload that upgrades a general money value into a journal-line amount. */
  public JournalLine(AccountCode accountCode, EntrySide side, Money amount) {
    this(accountCode, side, new PositiveMoney(amount));
  }

  /** Validates a journal line while keeping side and amount explicit. */
  public JournalLine {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(amount, "amount");
  }
}
