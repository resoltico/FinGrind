package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable domain identifier for one typed business event. */
public record BusinessEventId(String value) {
  /** Normalizes and validates one business-event identifier. */
  public BusinessEventId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Business event id must not be blank.");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException("Business event id must not exceed 128 characters.");
    }
  }
}
