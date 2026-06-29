package dev.erst.fingrind.contract.tax;

import java.util.Objects;

/** Stable code that selects one declared tax rule inside one registration. */
public record TaxCode(String value) {
  private static final int MAX_LENGTH = 120;
  private static final String PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

  /** Returns the canonical maximum UTF-16 length accepted for one tax code. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Returns the canonical public regex accepted for one tax code. */
  public static String pattern() {
    return PATTERN;
  }

  /** Validates one stable tax-code token. */
  public TaxCode {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tax code must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Tax code must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException("Tax code must use lowercase kebab-case tokens.");
    }
  }
}
