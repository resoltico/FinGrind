package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for one retained source document referenced by accounting facts. */
public record SourceDocumentId(String value) {
  private static final int MAX_LENGTH = 255;
  private static final String PATTERN = "^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,254})?$";

  /** Returns the canonical public regex accepted for one source-document id. */
  public static String pattern() {
    return PATTERN;
  }

  /** Returns the canonical maximum UTF-16 length accepted for one source-document id. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Validates a source-document id at the boundary where it is accepted or loaded. */
  public SourceDocumentId {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Source document id must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Source document id must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException(
          "Source document id must use ASCII letters or digits and may contain only '.', '_', ':', '/', or '-'.");
    }
  }
}
