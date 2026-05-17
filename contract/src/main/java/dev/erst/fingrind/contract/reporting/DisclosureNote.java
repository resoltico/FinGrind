package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.core.DisclosureNoteKind;
import dev.erst.fingrind.core.SourceDocument;
import java.util.List;
import java.util.Objects;

/** One typed disclosure note plus its evidence links. */
public record DisclosureNote(
    DisclosureNoteKind disclosureNoteKind,
    String title,
    List<String> paragraphs,
    List<SourceDocument> sourceDocuments) {
  /** Defensively copies one disclosure note. */
  public DisclosureNote {
    Objects.requireNonNull(disclosureNoteKind, "disclosureNoteKind");
    title = normalize(title, "title");
    paragraphs =
        List.copyOf(Objects.requireNonNull(paragraphs, "paragraphs")).stream()
            .map(value -> normalize(value, "paragraph"))
            .toList();
    sourceDocuments = List.copyOf(Objects.requireNonNull(sourceDocuments, "sourceDocuments"));
  }

  private static String normalize(String value, String fieldName) {
    String normalized = Objects.requireNonNull(value, fieldName).strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return normalized;
  }
}
