package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical legal or operating name for one accounting entity represented by one book. */
public record BookEntityName(String value) {
  private static final int MAX_LENGTH = 255;

  /** Validates one entity display name without imposing jurisdiction-specific vocabulary. */
  public BookEntityName {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Book entity name must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Book entity name must not exceed %d characters.".formatted(MAX_LENGTH));
    }
  }
}
