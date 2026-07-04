package dev.erst.fingrind.core;

import java.util.Set;

/** Total classifier outcome for one resolved journal. */
public record ClassificationResult(
    EconomicEventClass eventClass,
    Set<AnchorEntry> anchorSignature,
    Set<EconomicEventClass> containedTypedEvents,
    boolean hasCashLine,
    EvidenceClass evidenceClass,
    StructuralContext structural) {
  /** Validates one total classifier outcome. */
  public ClassificationResult {
    java.util.Objects.requireNonNull(eventClass, "eventClass");
    anchorSignature =
        Set.copyOf(java.util.Objects.requireNonNull(anchorSignature, "anchorSignature"));
    containedTypedEvents =
        Set.copyOf(java.util.Objects.requireNonNull(containedTypedEvents, "containedTypedEvents"));
    java.util.Objects.requireNonNull(evidenceClass, "evidenceClass");
    java.util.Objects.requireNonNull(structural, "structural");
  }
}
