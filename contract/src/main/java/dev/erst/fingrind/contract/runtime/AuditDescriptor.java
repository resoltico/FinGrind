package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Descriptor for caller-supplied versus committed audit fields. */
public record AuditDescriptor(
    List<FieldDescriptor> requestProvenanceFields, List<FieldDescriptor> committedFields)
    implements ResponseDescriptorType {
  /** Validates one audit descriptor payload. */
  public AuditDescriptor {
    requestProvenanceFields =
        ContractDescriptorValidation.copyList(requestProvenanceFields, "requestProvenanceFields");
    committedFields = ContractDescriptorValidation.copyList(committedFields, "committedFields");
  }
}
