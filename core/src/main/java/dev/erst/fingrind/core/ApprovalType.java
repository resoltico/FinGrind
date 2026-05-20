package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable caller-authored approval classification for accounting evidence links. */
public record ApprovalType(String value) {
  private static final int MAX_LENGTH = 64;
  private static final String PATTERN = "^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,63})?$";

  /** Returns the canonical public regex accepted for one approval type token. */
  public static String pattern() {
    return PATTERN;
  }

  /** Returns the canonical maximum UTF-16 length accepted for one approval type token. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Validates one approval type token. */
  public ApprovalType {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Approval type must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Approval type must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException(
          "Approval type must use ASCII letters or digits and may contain only '.', '_', ':', '/', or '-'.");
    }
  }
}
