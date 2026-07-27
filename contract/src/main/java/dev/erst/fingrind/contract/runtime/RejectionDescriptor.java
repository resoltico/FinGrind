package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** One stable machine rejection descriptor. */
public record RejectionDescriptor(
    String code,
    FailureCategory category,
    String description,
    List<FieldDescriptor> detailFields,
    List<RejectionDescriptor> detailRejections)
    implements ResponseDescriptorType {
  /** Creates one rejection descriptor with no structured detail payload. */
  public RejectionDescriptor(String code, FailureCategory category, String description) {
    this(code, category, description, List.of(), List.of());
  }

  /** Validates the structured rejection descriptor payload. */
  public RejectionDescriptor {
    code = ContractDescriptorValidation.requireText(code, "code");
    category = ContractDescriptorValidation.requireValue(category, "category");
    description = ContractDescriptorValidation.requireText(description, "description");
    detailFields = ContractDescriptorValidation.copyList(detailFields, "detailFields");
    detailRejections = ContractDescriptorValidation.copyList(detailRejections, "detailRejections");
  }
}
