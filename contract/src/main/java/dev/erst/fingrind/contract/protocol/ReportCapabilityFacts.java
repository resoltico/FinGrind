package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;
import java.util.Objects;

/** Structured reporting-capability fact published by the public bookkeeping kernel contract. */
public record ReportCapabilityFacts(
    String statementId,
    List<String> comparativeModes,
    String comparativeDefault,
    String description) {
  /** Validates one published reporting-capability fact. */
  public ReportCapabilityFacts {
    Objects.requireNonNull(statementId, "statementId");
    comparativeModes = List.copyOf(Objects.requireNonNull(comparativeModes, "comparativeModes"));
    if (comparativeModes.isEmpty()) {
      throw new IllegalArgumentException("comparativeModes must contain at least one mode.");
    }
    comparativeDefault =
        ContractDescriptorValidation.requireText(comparativeDefault, "comparativeDefault");
    if (!comparativeModes.contains(comparativeDefault)) {
      throw new IllegalArgumentException("comparativeDefault must be present in comparativeModes.");
    }
    Objects.requireNonNull(description, "description");
  }
}
