package dev.erst.fingrind.core;

import java.util.Objects;

/** Durable reference to one retained approval linked to accounting facts. */
public record ApprovalReference(ApprovalId approvalId, ApprovalType approvalType) {
  /** Validates one approval reference. */
  public ApprovalReference {
    Objects.requireNonNull(approvalId, "approvalId");
    Objects.requireNonNull(approvalType, "approvalType");
  }
}
