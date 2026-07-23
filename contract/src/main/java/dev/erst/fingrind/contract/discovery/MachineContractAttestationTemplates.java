package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.Objects;

/** Canonical machine-contract templates for the separate attestation administration surface. */
public final class MachineContractAttestationTemplates {
  private MachineContractAttestationTemplates() {}

  /** Builds the canonical credential-registry or authority-policy request template. */
  public static TemplateDescriptorType registryTemplate(OperationId commandTopic) {
    return ContractAttestationRegistryTemplates.template(
        Objects.requireNonNull(commandTopic, "commandTopic"));
  }

  /** Builds the complete review-file scaffold accepted by attestation verification commands. */
  public static ContractAttestationReviewTemplates.AttestationReviewFileTemplateDescriptor
      reviewFileTemplate() {
    return ContractAttestationReviewTemplates.reviewFileTemplate();
  }
}
