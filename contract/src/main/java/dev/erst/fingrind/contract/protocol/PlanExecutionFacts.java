package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Core-owned ledger-plan execution semantics advertised to AI-agent callers. */
public record PlanExecutionFacts(
    String transactionMode, String failurePolicy, String journal, List<String> hardLimitations) {
  /** Validates plan-execution metadata. */
  public PlanExecutionFacts {
    transactionMode = ContractDescriptorValidation.requireText(transactionMode, "transactionMode");
    failurePolicy = ContractDescriptorValidation.requireText(failurePolicy, "failurePolicy");
    journal = ContractDescriptorValidation.requireText(journal, "journal");
    hardLimitations = ContractDescriptorValidation.copyList(hardLimitations, "hardLimitations");
  }
}
