package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical human-visible document number for one source document. */
public record SourceDocumentNumber(String value) {
  /** Normalizes and validates one source-document number. */
  public SourceDocumentNumber {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Source document number must not be blank.");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException("Source document number must not exceed 128 characters.");
    }
  }
}
