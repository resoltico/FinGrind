package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.UUID;

/** Canonical UUID identifier for one committed posting fact. */
public record PostingId(String value) {
  /** Validates a posting identifier at the boundary where it is created or loaded. */
  public PostingId {
    try {
      value = UUID.fromString(Objects.requireNonNull(value, "value").strip()).toString();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Posting id must be one canonical UUID.", exception);
    }
  }
}
