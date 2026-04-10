package dev.erst.fingrind.core;

import java.util.Objects;

/** Reversal linkage from a new posting fact to an earlier committed posting. */
public record ReversalReference(PostingId priorPostingId) {
  /** Validates one reversal linkage without redefining the core journal grammar. */
  public ReversalReference {
    Objects.requireNonNull(priorPostingId, "priorPostingId");
  }
}
