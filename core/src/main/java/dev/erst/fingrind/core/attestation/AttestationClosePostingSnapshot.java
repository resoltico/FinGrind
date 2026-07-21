package dev.erst.fingrind.core.attestation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Exact generated posting effect carried by one reporting-period close operation. */
public record AttestationClosePostingSnapshot(
    UUID postingId,
    UUID commandId,
    String idempotencyKey,
    String causationId,
    String postingKind,
    String postingOriginKind,
    LocalDate effectiveDate,
    Instant recordedAt,
    String sourceChannel,
    List<AttestationPostingLine> journalLines) {
  /** Defensively owns the complete persisted facts that belong to one generated close posting. */
  public AttestationClosePostingSnapshot {
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(commandId, "commandId");
    idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    causationId = requireText(causationId, "causationId");
    postingKind = requireText(postingKind, "postingKind");
    postingOriginKind = requireText(postingOriginKind, "postingOriginKind");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    recordedAt = Objects.requireNonNull(recordedAt, "recordedAt").truncatedTo(ChronoUnit.MILLIS);
    sourceChannel = requireText(sourceChannel, "sourceChannel");
    journalLines = List.copyOf(Objects.requireNonNull(journalLines, "journalLines"));
    if (journalLines.size() < 2) {
      throw new IllegalArgumentException(
          "A generated close posting must contain at least two lines.");
    }
  }

  private static String requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank.");
    }
    return value;
  }
}
