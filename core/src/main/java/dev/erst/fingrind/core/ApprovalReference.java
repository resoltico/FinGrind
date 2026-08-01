package dev.erst.fingrind.core;

import java.time.Instant;
import java.util.Objects;

/** Durable retained approval fact linked to accepted accounting evidence. */
public record ApprovalReference(
    ApprovalId approvalId,
    ApprovalType approvalType,
    String approverReference,
    String approverType,
    ApprovalDecision decision,
    Instant approvedAt) {
  /** Validates one retained approval fact. */
  public ApprovalReference {
    Objects.requireNonNull(approvalId, "approvalId");
    Objects.requireNonNull(approvalType, "approvalType");
    approverReference = requireText(approverReference, "approverReference");
    approverType = requireText(approverType, "approverType");
    Objects.requireNonNull(decision, "decision");
    Objects.requireNonNull(approvedAt, "approvedAt");
  }

  private static String requireText(String value, String name) {
    String checked = Objects.requireNonNull(value, name).strip();
    if (checked.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank.");
    }
    return checked;
  }
}
