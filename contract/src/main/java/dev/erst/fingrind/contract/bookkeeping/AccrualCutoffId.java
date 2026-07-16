package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Stable caller-chosen identifier for one accrual cut-off lifecycle. */
public record AccrualCutoffId(String value) {
  private static final int MAX_LENGTH = 120;
  private static final String PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

  /** Returns the canonical maximum UTF-16 length accepted for one cut-off id. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Returns the canonical public regex accepted for one cut-off id. */
  public static String pattern() {
    return PATTERN;
  }

  /** Validates one stable cut-off identifier. */
  public AccrualCutoffId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Accrual cut-off id must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Accrual cut-off id must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException(
          "Accrual cut-off id must use lowercase kebab-case tokens.");
    }
  }
}
