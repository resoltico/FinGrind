package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier linking one posting request to its immediate cause. */
public record CausationId(String value) {
  /** Validates a causation identifier at the boundary where it is accepted or loaded. */
  public CausationId {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Causation id must not be blank.");
    }
  }
}
