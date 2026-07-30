package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.FieldDescriptor;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import java.util.List;

/** One stable entry-semantics rejection definition. */
record EntrySemanticsViolationDefinition(
    String code, String category, String description, String repair) {
  EntrySemanticsViolationDefinition {
    code = ContractDescriptorValidation.requireText(code, "code");
    category = ContractDescriptorValidation.requireText(category, "category");
    description = ContractDescriptorValidation.requireText(description, "description");
    repair = ContractDescriptorValidation.requireText(repair, "repair");
  }

  RejectionDescriptor descriptor(List<FieldDescriptor> detailFields) {
    return new RejectionDescriptor(
        code, FailureCategory.DOMAIN_SEMANTIC, 2, description, detailFields, List.of());
  }
}
