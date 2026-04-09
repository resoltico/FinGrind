package dev.erst.fingrind.core;

import java.util.Objects;

/** One debit or credit line in a journal entry. */
public record JournalLine(AccountCode accountCode, EntrySide side, Money amount) {
  /** Side of the journal equation carried by one line. */
  public enum EntrySide {
    DEBIT,
    CREDIT
  }

  /** Validates a journal line while keeping side and amount explicit. */
  public JournalLine {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(amount, "amount");
    if (amount.amount().signum() == 0) {
      throw new IllegalArgumentException("Journal line amount must be greater than zero.");
    }
  }
}
