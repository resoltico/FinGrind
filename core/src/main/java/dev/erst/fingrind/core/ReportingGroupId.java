package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for one organization-level reporting group. */
public record ReportingGroupId(String value) {
  /** Normalizes and validates one reporting-group identifier. */
  public ReportingGroupId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Reporting group id must not be blank.");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException("Reporting group id must not exceed 128 characters.");
    }
  }
}
