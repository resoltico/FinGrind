package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable caller-supplied identity used to reject duplicate posting attempts. */
public record IdempotencyKey(String value) {
  /** Validates an idempotency key at the boundary where a request enters the system. */
  public IdempotencyKey {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Idempotency key must not be blank.");
    }
  }
}
