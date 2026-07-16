package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.ContractResponse;
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

  ContractResponse.RejectionDescriptor descriptor(
      List<ContractResponse.FieldDescriptor> detailFields) {
    return new ContractResponse.RejectionDescriptor(
        code,
        ContractResponse.FailureCategory.DOMAIN_SEMANTIC,
        description,
        detailFields,
        List.of());
  }
}
