package dev.erst.fingrind.core;

import java.time.Instant;
import java.util.Objects;

/** Durable audit metadata attached to one committed posting fact. */
public record CommittedProvenance(
    RequestProvenance requestProvenance, Instant recordedAt, SourceChannel sourceChannel) {
  /** Validates and normalizes the durable audit metadata created at commit time. */
  public CommittedProvenance {
    Objects.requireNonNull(requestProvenance, "requestProvenance");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(sourceChannel, "sourceChannel");
  }
}
