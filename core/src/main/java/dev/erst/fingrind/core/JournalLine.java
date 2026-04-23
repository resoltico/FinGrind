package dev.erst.fingrind.core;

import java.util.List;
import java.util.Objects;

/** One debit or credit line in a journal entry. */
public record JournalLine(AccountCode accountCode, EntrySide side, PositiveMoney amount) {
  /** Side of the journal equation carried by one line. */
  public enum EntrySide implements WireValue {
    DEBIT,
    CREDIT;

    /** Returns the stable public wire value for this journal-line side. */
    @Override
    public String wireValue() {
      return switch (this) {
        case DEBIT -> "DEBIT";
        case CREDIT -> "CREDIT";
      };
    }

    /** Returns every stable public wire value in declaration order. */
    public static List<String> wireValues() {
      return WireValue.wireValues(EntrySide.class);
    }

    /** Parses one stable public wire value. */
    public static EntrySide fromWireValue(String wireValue) {
      return WireValue.fromWireValue(EntrySide.class, wireValue, "Unsupported line side");
    }
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
