package dev.erst.fingrind.core;

import java.util.Objects;

/** Human-readable reason recorded for a reversal posting. */
public record ReversalReason(String value) {
  /** Validates one reversal reason while preserving the submitted wording. */
  public ReversalReason {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Reversal reason must not be blank.");
    }
  }
}
