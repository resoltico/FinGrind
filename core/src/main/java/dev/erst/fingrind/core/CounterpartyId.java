package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for one external counterparty. */
public record CounterpartyId(String value) {
  /** Normalizes and validates one counterparty identifier. */
  public CounterpartyId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Counterparty id must not be blank.");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException("Counterparty id must not exceed 128 characters.");
    }
  }
}
