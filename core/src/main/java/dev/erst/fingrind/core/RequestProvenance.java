package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.Optional;

/** Caller-supplied provenance accepted at the posting request boundary. */
public record RequestProvenance(
    ActorId actorId,
    ActorType actorType,
    CommandId commandId,
    IdempotencyKey idempotencyKey,
    CausationId causationId,
    Optional<CorrelationId> correlationId,
    Optional<CorrectionReason> reason) {
  /** Validates and normalizes request provenance before it reaches the commit path. */
  public RequestProvenance {
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(actorType, "actorType");
    Objects.requireNonNull(commandId, "commandId");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    Objects.requireNonNull(causationId, "causationId");
    correlationId = correlationId == null ? Optional.empty() : correlationId;
    reason = reason == null ? Optional.empty() : reason;
  }
}
