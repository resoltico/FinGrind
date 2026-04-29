package dev.erst.fingrind.core;

import java.util.Objects;

/** Ledger account identifier attached to one journal line. */
public record AccountCode(String value) {
  private static final int MAX_LENGTH = 255;
  private static final String PATTERN = "^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,254})?$";

  /** Returns the canonical maximum UTF-16 length accepted for one account code. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Returns the canonical public regex accepted for one account code. */
  public static String pattern() {
    return PATTERN;
  }

  /** Validates an account code without imposing a jurisdiction-specific format. */
  public AccountCode {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Account code must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Account code must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException(
          "Account code must use ASCII letters or digits and may contain only '.', '_', ':', '/', or '-'.");
    }
  }
}
