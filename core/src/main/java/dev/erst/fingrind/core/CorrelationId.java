package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier linking one posting request to a broader operation. */
public record CorrelationId(String value) {
  /** Validates a correlation identifier at the boundary where it is accepted or loaded. */
  public CorrelationId {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Correlation id must not be blank.");
    }
  }
}
