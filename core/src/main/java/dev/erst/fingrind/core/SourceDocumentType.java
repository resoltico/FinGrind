package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable caller-authored source-document classification for accounting evidence links. */
public record SourceDocumentType(String value) {
  private static final int MAX_LENGTH = 64;
  private static final String PATTERN = "^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,63})?$";

  /** Returns the canonical public regex accepted for one source-document type token. */
  public static String pattern() {
    return PATTERN;
  }

  /** Returns the canonical maximum UTF-16 length accepted for one source-document type token. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Validates one source-document type token. */
  public SourceDocumentType {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Source document type must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Source document type must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException(
          "Source document type must use ASCII letters or digits and may contain only '.', '_', ':', '/', or '-'.");
    }
  }
}
