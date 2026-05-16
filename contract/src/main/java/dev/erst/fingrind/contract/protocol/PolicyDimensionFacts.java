package dev.erst.fingrind.contract.protocol;

import java.util.Objects;

/** Structured status for one accounting-policy dimension published by the default policy pack. */
public record PolicyDimensionFacts(
    String dimensionId, CapabilityStatus status, String description) {
  /** Validates one published policy-dimension fact. */
  public PolicyDimensionFacts {
    Objects.requireNonNull(dimensionId, "dimensionId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(description, "description");
  }
}
