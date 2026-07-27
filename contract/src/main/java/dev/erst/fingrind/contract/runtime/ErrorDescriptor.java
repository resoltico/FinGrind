package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** One stable machine error descriptor. */
public record ErrorDescriptor(
    String code,
    FailureCategory category,
    int exitCode,
    String description,
    List<FieldDescriptor> detailFields)
    implements ResponseDescriptorType {
  /** Creates one error descriptor with no structured detail payload. */
  public ErrorDescriptor(String code, FailureCategory category, int exitCode, String description) {
    this(code, category, exitCode, description, List.of());
  }

  /** Validates the structured error descriptor payload. */
  public ErrorDescriptor {
    code = ContractDescriptorValidation.requireText(code, "code");
    category = ContractDescriptorValidation.requireValue(category, "category");
    if (exitCode < 0) {
      throw new IllegalArgumentException("exitCode must not be negative.");
    }
    description = ContractDescriptorValidation.requireText(description, "description");
    detailFields = ContractDescriptorValidation.copyList(detailFields, "detailFields");
  }
}
