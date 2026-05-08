package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;
import java.util.Objects;

/** Human-facing documentation facts for one canonical public protocol operation. */
public record ProtocolOperationDocumentation(
    String analysisSummary, List<ProtocolExampleStep> exampleSteps) {
  /** Validates one operation-documentation descriptor. */
  public ProtocolOperationDocumentation {
    analysisSummary = requireText(analysisSummary, "analysisSummary");
    exampleSteps = ContractDescriptorValidation.copyList(exampleSteps, "exampleSteps");
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }
}
