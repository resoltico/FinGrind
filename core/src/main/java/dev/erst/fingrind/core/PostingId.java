package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for one committed posting fact. */
public record PostingId(String value) {
  /** Validates a posting identifier at the boundary where it is created or loaded. */
  public PostingId {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Posting id must not be blank.");
    }
  }
}
