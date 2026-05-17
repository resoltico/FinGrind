package dev.erst.fingrind.core;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Canonical reference to one business source document that supports an accounting event. */
public record SourceDocument(
    SourceDocumentId sourceDocumentId,
    SourceDocumentType sourceDocumentType,
    LocalDate documentDate,
    SourceDocumentNumber documentNumber,
    Optional<String> description) {
  /** Validates one source-document reference. */
  public SourceDocument {
    Objects.requireNonNull(sourceDocumentId, "sourceDocumentId");
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
    Objects.requireNonNull(documentDate, "documentDate");
    Objects.requireNonNull(documentNumber, "documentNumber");
    Objects.requireNonNull(description, "description");
    description =
        description.map(
            value -> {
              String normalized = value.strip();
              if (normalized.isEmpty()) {
                throw new IllegalArgumentException(
                    "Source document description must not be blank when present.");
              }
              if (normalized.length() > 512) {
                throw new IllegalArgumentException(
                    "Source document description must not exceed 512 characters.");
              }
              return normalized;
            });
  }
}
