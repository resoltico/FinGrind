package dev.erst.fingrind.core;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Durable retained source-document fact linked to accepted accounting evidence. */
public record SourceDocumentReference(
    SourceDocumentId sourceDocumentId,
    SourceDocumentType sourceDocumentType,
    LocalDate documentDate,
    Instant capturedAt,
    StorageLocator storageLocator,
    ContentSha256 contentSha256) {
  /** Validates one retained source-document fact. */
  public SourceDocumentReference {
    Objects.requireNonNull(sourceDocumentId, "sourceDocumentId");
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
    Objects.requireNonNull(documentDate, "documentDate");
    Objects.requireNonNull(capturedAt, "capturedAt");
    Objects.requireNonNull(storageLocator, "storageLocator");
    Objects.requireNonNull(contentSha256, "contentSha256");
  }
}
