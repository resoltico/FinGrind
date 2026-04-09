package dev.erst.fingrind.core;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** ISO-style currency identifier for exact monetary values. */
public record CurrencyCode(String value) {
  private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z]{3}");

  /** Validates and normalizes a currency code to its canonical uppercase form. */
  public CurrencyCode {
    Objects.requireNonNull(value, "value");
    value = value.strip().toUpperCase(Locale.ROOT);
    if (!CODE_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Currency code must contain exactly three uppercase letters.");
    }
  }
}
