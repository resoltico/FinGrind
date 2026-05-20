package dev.erst.fingrind.core;

import java.util.Objects;

/** Durable reference to one retained source document linked to accounting facts. */
public record SourceDocumentReference(
    SourceDocumentId sourceDocumentId, SourceDocumentType sourceDocumentType) {
  /** Validates one source-document reference. */
  public SourceDocumentReference {
    Objects.requireNonNull(sourceDocumentId, "sourceDocumentId");
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
  }
}
