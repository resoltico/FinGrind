package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationCommitMode;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationCredentialMode;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationDisposition;
import java.util.Arrays;
import java.util.List;

/** One complete successful ledger-plan attestation outcome contract. */
public record PlanAttestationOutcomeDescriptor(
    LedgerPlanAttestationDisposition disposition,
    LedgerPlanAttestationCommitMode attestationCommit,
    LedgerPlanAttestationCredentialMode attestationCredentials)
    implements ResponseDescriptorType {
  private static final List<PlanAttestationOutcomeDescriptor> STANDARD_OUTCOMES =
      Arrays.stream(LedgerPlanAttestationDisposition.values())
          .map(PlanAttestationOutcomeDescriptor::forDisposition)
          .toList();

  /** Validates one complete plan outcome pairing. */
  public PlanAttestationOutcomeDescriptor {
    disposition = ContractDescriptorValidation.requireValue(disposition, "disposition");
    attestationCommit =
        ContractDescriptorValidation.requireValue(attestationCommit, "attestationCommit");
    attestationCredentials =
        ContractDescriptorValidation.requireValue(attestationCredentials, "attestationCredentials");
    if (attestationCommit != disposition.attestationCommitMode()
        || attestationCredentials != disposition.attestationCredentialMode()) {
      throw new IllegalArgumentException(
          "The plan attestation outcome must declare the canonical commitment and credential requirements.");
    }
  }

  private static PlanAttestationOutcomeDescriptor forDisposition(
      LedgerPlanAttestationDisposition disposition) {
    return new PlanAttestationOutcomeDescriptor(
        disposition, disposition.attestationCommitMode(), disposition.attestationCredentialMode());
  }

  /** Returns the complete plan-outcome vocabulary in stable disposition order. */
  public static List<PlanAttestationOutcomeDescriptor> standardOutcomes() {
    return STANDARD_OUTCOMES;
  }
}
