package dev.erst.fingrind.core.attestation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The durable posting fact produced by one attested posting request. */
public record AttestationPostingEffectSnapshot(
    UUID postingId,
    String operationKind,
    String postingKind,
    String postingOriginKind,
    Instant recordedAt,
    @Nullable UUID priorPostingId,
    UUID commandId) {
  /** Requires the durable identifiers and metadata that the effect fact commits. */
  public AttestationPostingEffectSnapshot {
    Objects.requireNonNull(postingId, "postingId");
    requireText(operationKind, "operationKind");
    requireText(postingKind, "postingKind");
    requireText(postingOriginKind, "postingOriginKind");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(commandId, "commandId");
  }

  private static void requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank.");
    }
  }
}
