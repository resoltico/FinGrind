package dev.erst.fingrind.core;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Durable provenance attached to one posting attempt. */
public record ProvenanceEnvelope(
    String actorId,
    ActorType actorType,
    String commandId,
    IdempotencyKey idempotencyKey,
    String causationId,
    Optional<String> correlationId,
    Instant recordedAt,
    Optional<String> reason,
    SourceChannel sourceChannel) {
  /** Actor classification for durable audit provenance. */
  public enum ActorType {
    USER,
    SYSTEM,
    AGENT
  }

  /** Operating surface from which a posting request entered the system. */
  public enum SourceChannel {
    CLI,
    API,
    TEST
  }

  /** Validates and normalizes provenance fields required for durable audit history. */
  public ProvenanceEnvelope {
    actorId = normalizeRequired(actorId, "Actor id must not be blank.");
    Objects.requireNonNull(actorType, "actorType");
    commandId = normalizeRequired(commandId, "Command id must not be blank.");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    causationId = normalizeRequired(causationId, "Causation id must not be blank.");
    correlationId = normalizeOptional(correlationId);
    Objects.requireNonNull(recordedAt, "recordedAt");
    reason = normalizeOptional(reason);
    Objects.requireNonNull(sourceChannel, "sourceChannel");
  }

  private static String normalizeRequired(String value, String message) {
    Objects.requireNonNull(value, "value");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return normalized;
  }

  private static Optional<String> normalizeOptional(Optional<String> value) {
    Optional<String> safeValue = value == null ? Optional.empty() : value;
    return safeValue.map(String::strip).filter(normalized -> !normalized.isEmpty());
  }
}
