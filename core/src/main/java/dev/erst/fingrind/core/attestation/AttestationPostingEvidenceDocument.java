package dev.erst.fingrind.core.attestation;

import java.time.LocalDate;
import java.util.Objects;

/** One durable source-document fact committed by an attested posting operation. */
public record AttestationPostingEvidenceDocument(
    String sourceDocumentId, String sourceDocumentType, LocalDate documentDate) {
  /** Requires one complete source-document commitment. */
  public AttestationPostingEvidenceDocument {
    requireText(sourceDocumentId, "sourceDocumentId");
    requireText(sourceDocumentType, "sourceDocumentType");
    Objects.requireNonNull(documentDate, "documentDate");
  }

  private static void requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank.");
    }
  }
}
