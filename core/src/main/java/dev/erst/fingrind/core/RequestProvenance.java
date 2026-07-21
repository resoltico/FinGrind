package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.Optional;

/** Caller-supplied provenance accepted at the posting request boundary. */
public record RequestProvenance(
    CommandId commandId,
    IdempotencyKey idempotencyKey,
    CausationId causationId,
    Optional<CorrelationId> correlationId) {
  /** Validates and normalizes request provenance before it reaches the commit path. */
  public RequestProvenance {
    Objects.requireNonNull(commandId, "commandId");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    Objects.requireNonNull(causationId, "causationId");
    Objects.requireNonNull(correlationId, "correlationId");
  }
}
