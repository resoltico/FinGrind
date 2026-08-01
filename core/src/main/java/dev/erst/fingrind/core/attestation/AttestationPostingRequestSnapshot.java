package dev.erst.fingrind.core.attestation;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The complete caller-visible posting request that one operation commits. */
public record AttestationPostingRequestSnapshot(
    String operationKind,
    String idempotencyKey,
    String causationId,
    String sourceChannel,
    LocalDate effectiveDate,
    String postingKind,
    @Nullable String priorPostingId,
    @Nullable String reversalReason,
    List<AttestationPostingEvidenceDocument> sourceDocuments,
    List<AttestationPostingLine> journalLines) {
  /** Defensively owns every semantic fact admitted for one posting request. */
  public AttestationPostingRequestSnapshot {
    requireText(operationKind, "operationKind");
    requireText(idempotencyKey, "idempotencyKey");
    requireText(causationId, "causationId");
    requireText(sourceChannel, "sourceChannel");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    requireText(postingKind, "postingKind");
    if (priorPostingId == null && reversalReason != null) {
      throw new IllegalArgumentException("reversalReason requires priorPostingId.");
    }
    if (priorPostingId != null && reversalReason == null) {
      throw new IllegalArgumentException("priorPostingId requires reversalReason.");
    }
    sourceDocuments = List.copyOf(Objects.requireNonNull(sourceDocuments, "sourceDocuments"));
    journalLines = List.copyOf(Objects.requireNonNull(journalLines, "journalLines"));
    if (sourceDocuments.isEmpty()) {
      throw new IllegalArgumentException("sourceDocuments must not be empty.");
    }
    if (journalLines.isEmpty()) {
      throw new IllegalArgumentException("journalLines must not be empty.");
    }
  }

  private static void requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank.");
    }
  }
}
