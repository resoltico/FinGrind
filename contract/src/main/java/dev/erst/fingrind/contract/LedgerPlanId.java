package dev.erst.fingrind.contract;

import java.util.Objects;

/** Stable caller-supplied identifier for one AI-authored ledger plan. */
public record LedgerPlanId(String value) {
  /** Validates one ledger plan identifier at the boundary where it enters the system. */
  public LedgerPlanId {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Ledger plan id must not be blank.");
    }
  }
}
