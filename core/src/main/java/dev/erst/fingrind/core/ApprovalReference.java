package dev.erst.fingrind.core;

import java.time.Instant;
import java.util.Objects;

/** Durable retained approval fact linked to accepted accounting evidence. */
public record ApprovalReference(
    ApprovalId approvalId,
    ApprovalType approvalType,
    ActorId approverId,
    ActorType approverType,
    ApprovalDecision decision,
    Instant approvedAt) {
  /** Validates one retained approval fact. */
  public ApprovalReference {
    Objects.requireNonNull(approvalId, "approvalId");
    Objects.requireNonNull(approvalType, "approvalType");
    Objects.requireNonNull(approverId, "approverId");
    Objects.requireNonNull(approverType, "approverType");
    Objects.requireNonNull(decision, "decision");
    Objects.requireNonNull(approvedAt, "approvedAt");
  }
}
