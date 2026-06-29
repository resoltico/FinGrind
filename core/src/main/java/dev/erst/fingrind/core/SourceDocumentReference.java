package dev.erst.fingrind.core;

import java.time.LocalDate;
import java.util.Objects;

/** Durable retained source-document fact linked to accepted accounting evidence. */
public record SourceDocumentReference(
    SourceDocumentId sourceDocumentId,
    SourceDocumentType sourceDocumentType,
    LocalDate documentDate) {
  /** Validates one retained source-document fact. */
  public SourceDocumentReference {
    Objects.requireNonNull(sourceDocumentId, "sourceDocumentId");
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
    Objects.requireNonNull(documentDate, "documentDate");
  }
}
