package dev.erst.fingrind.executor.bookkeeping.policy;

/** Operational seam for source-document and approval evidence policy. */
@FunctionalInterface
public interface EvidencePolicy {
  /** Returns whether the current pack requires first-class source evidence on postings. */
  boolean requiresFirstClassEvidence();
}
