package dev.erst.fingrind.contract;

import java.util.Objects;

/** One compact name/value fact recorded for a ledger-plan step. */
public record LedgerFact(String name, String value) {
  /** Validates one journal fact. */
  public LedgerFact {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    if (name.isBlank()) {
      throw new IllegalArgumentException("Ledger fact name must not be blank.");
    }
  }
}
