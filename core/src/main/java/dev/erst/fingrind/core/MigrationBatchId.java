package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for one migration or adoption batch. */
public record MigrationBatchId(String value) {
  /** Normalizes and validates one migration-batch identifier. */
  public MigrationBatchId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Migration batch id must not be blank.");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException("Migration batch id must not exceed 128 characters.");
    }
  }
}
