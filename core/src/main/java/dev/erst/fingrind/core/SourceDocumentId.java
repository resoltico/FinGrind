package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for one source-document reference. */
public record SourceDocumentId(String value) {
  /** Normalizes and validates one source-document identifier. */
  public SourceDocumentId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Source document id must not be blank.");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException("Source document id must not exceed 128 characters.");
    }
  }
}
