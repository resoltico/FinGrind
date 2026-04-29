package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable caller-supplied identity used to reject duplicate posting attempts. */
public record IdempotencyKey(String value) {
  private static final int MAX_LENGTH = 128;
  private static final String PATTERN = "^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,127})?$";

  /** Returns the canonical maximum UTF-16 length accepted for one idempotency key. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Returns the canonical public regex accepted for one idempotency key. */
  public static String pattern() {
    return PATTERN;
  }

  /** Validates an idempotency key at the boundary where a request enters the system. */
  public IdempotencyKey {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Idempotency key must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Idempotency key must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException(
          "Idempotency key must use ASCII letters or digits and may contain only '.', '_', ':', '/', or '-'.");
    }
  }
}
