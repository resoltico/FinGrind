package dev.erst.fingrind.contract.protocol;

import java.util.Objects;

/** Structured reporting-capability fact published by the public bookkeeping kernel contract. */
public record ReportCapabilityFacts(
    String statementId, boolean comparativeSupported, String description) {
  /** Validates one published reporting-capability fact. */
  public ReportCapabilityFacts {
    Objects.requireNonNull(statementId, "statementId");
    Objects.requireNonNull(description, "description");
  }
}
