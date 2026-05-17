package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for one organization graph above book-level accounting entities. */
public record OrganizationId(String value) {
  /** Normalizes and validates one organization identifier. */
  public OrganizationId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Organization id must not be blank.");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException("Organization id must not exceed 128 characters.");
    }
  }
}
