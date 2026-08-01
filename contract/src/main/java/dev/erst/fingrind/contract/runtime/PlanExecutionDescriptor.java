package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.PlanFailurePolicy;
import dev.erst.fingrind.contract.protocol.PlanTransactionMode;
import java.util.List;

/** Descriptor for ledger-plan execution semantics. */
public record PlanExecutionDescriptor(
    PlanTransactionMode transactionMode,
    PlanFailurePolicy failurePolicy,
    String journal,
    List<PlanAttestationOutcomeDescriptor> attestationOutcomes,
    List<String> hardLimitations)
    implements ResponseDescriptorType {
  /** Validates one plan-execution descriptor payload. */
  public PlanExecutionDescriptor {
    transactionMode = ContractDescriptorValidation.requireValue(transactionMode, "transactionMode");
    failurePolicy = ContractDescriptorValidation.requireValue(failurePolicy, "failurePolicy");
    journal = ContractDescriptorValidation.requireText(journal, "journal");
    attestationOutcomes =
        ContractDescriptorValidation.copyList(attestationOutcomes, "attestationOutcomes");
    if (!attestationOutcomes.equals(PlanAttestationOutcomeDescriptor.standardOutcomes())) {
      throw new IllegalArgumentException(
          "attestationOutcomes must declare the complete ledger-plan commitment and credential contract.");
    }
    hardLimitations = ContractDescriptorValidation.copyList(hardLimitations, "hardLimitations");
  }
}
