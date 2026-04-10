package dev.erst.fingrind.core;

import java.util.Objects;

/** Human-readable reason recorded for a corrective posting. */
public record CorrectionReason(String value) {
  /** Validates one corrective reason while preserving the submitted wording. */
  public CorrectionReason {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Correction reason must not be blank.");
    }
  }
}
