package dev.erst.fingrind.core;

/** Canonical legal or operating name for one accounting entity represented by one book. */
public record BookEntityName(String value) {
  private static final int MAX_LENGTH = 255;

  /** Validates one entity display name without imposing jurisdiction-specific vocabulary. */
  public BookEntityName {
    value = CanonicalDisplayText.require(value, "Book entity name");
    if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Book entity name must not exceed %d characters.".formatted(MAX_LENGTH));
    }
  }

  /** Reads an existing durable entity name without restoring unsafe terminal control bytes. */
  public static BookEntityName fromPersisted(String value) {
    return new BookEntityName(CanonicalDisplayText.sanitizePersisted(value));
  }
}
