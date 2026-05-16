package dev.erst.fingrind.contract.protocol;

import java.util.Objects;

/** Structured fact for one published accounting-policy or extension seam. */
public record PolicySeamFacts(
    String seamId, CapabilityStatus status, String boundedContextOwner, String description) {
  /** Validates one published policy-seam fact. */
  public PolicySeamFacts {
    Objects.requireNonNull(seamId, "seamId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(boundedContextOwner, "boundedContextOwner");
    Objects.requireNonNull(description, "description");
  }
}
