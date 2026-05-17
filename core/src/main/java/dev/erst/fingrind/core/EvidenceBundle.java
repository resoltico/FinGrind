package dev.erst.fingrind.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Canonical audit-evidence bundle that supports one typed accounting event. */
public record EvidenceBundle(
    Optional<Counterparty> counterparty,
    List<SourceDocument> sourceDocuments,
    Optional<Approval> approval) {
  /** Validates and defensively copies one evidence bundle. */
  public EvidenceBundle {
    Objects.requireNonNull(counterparty, "counterparty");
    sourceDocuments = List.copyOf(Objects.requireNonNull(sourceDocuments, "sourceDocuments"));
    Objects.requireNonNull(approval, "approval");
  }

  /** Returns the canonical empty bundle for events that genuinely require none. */
  public static EvidenceBundle empty() {
    return new EvidenceBundle(Optional.empty(), List.of(), Optional.empty());
  }
}
