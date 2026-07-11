package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Reversal-specific request template descriptors. */
public interface ContractReversalTemplates {
  record ReversalTemplateDescriptor(String priorPostingId, String reason)
      implements TemplateDescriptorType {
    public ReversalTemplateDescriptor {
      priorPostingId = ContractDescriptorValidation.requireText(priorPostingId, "priorPostingId");
      reason = ContractDescriptorValidation.requireText(reason, "reason");
    }
  }
}
