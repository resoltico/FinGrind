package dev.erst.fingrind.core;

import java.util.List;
import java.util.Objects;

/** First-class source-document and approval references attached to accepted accounting facts. */
public record AccountingEvidence(
    List<SourceDocumentReference> sourceDocuments, List<ApprovalReference> approvals) {
  /** Validates one accounting-evidence bundle. */
  public AccountingEvidence {
    Objects.requireNonNull(sourceDocuments, "sourceDocuments");
    Objects.requireNonNull(approvals, "approvals");
    sourceDocuments = List.copyOf(sourceDocuments);
    approvals = List.copyOf(approvals);
    if (sourceDocuments.isEmpty()) {
      throw new IllegalArgumentException(
          "Accounting evidence must contain at least one source document.");
    }
  }

  /** Returns whether at least one approval reference is attached. */
  public boolean hasApprovals() {
    return !approvals.isEmpty();
  }
}
