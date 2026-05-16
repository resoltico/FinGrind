package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Structured reporting-capability fact published by the public accounting baseline. */
public record ReportCapabilityFacts(
    String statementId,
    CapabilityStatus status,
    boolean requiredForTargetBaseline,
    String boundedContextOwner,
    List<String> blockingModelGaps,
    String description) {
  /** Validates one published reporting-capability fact. */
  public ReportCapabilityFacts {
    Objects.requireNonNull(statementId, "statementId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(boundedContextOwner, "boundedContextOwner");
    blockingModelGaps = List.copyOf(Objects.requireNonNull(blockingModelGaps, "blockingModelGaps"));
    Objects.requireNonNull(description, "description");
  }
}
