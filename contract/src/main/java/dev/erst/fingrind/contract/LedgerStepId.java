package dev.erst.fingrind.contract;

import java.util.Objects;

/** Stable caller-supplied identifier for one executable ledger-plan step. */
public record LedgerStepId(String value) {
  /** Validates one ledger step identifier at the boundary where it enters the system. */
  public LedgerStepId {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Ledger step id must not be blank.");
    }
  }
}
