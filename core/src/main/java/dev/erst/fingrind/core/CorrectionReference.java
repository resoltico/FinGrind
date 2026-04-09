package dev.erst.fingrind.core;

import java.util.Objects;

/** Additive linkage from a new posting fact to an earlier committed posting. */
public record CorrectionReference(CorrectionKind kind, PostingId priorPostingId) {
  /** Correction form applied by a new posting fact. */
  public enum CorrectionKind {
    REVERSAL,
    AMENDMENT
  }

  /** Validates a correction linkage without redefining the core journal grammar. */
  public CorrectionReference {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(priorPostingId, "priorPostingId");
  }
}
