package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Shared immutable facts describing sanctioned current executable policy seams. */
public record ExtensionSurfaceFacts(
    String model,
    String defaultPolicyPackId,
    List<String> implementedSeams,
    List<PolicySeamFacts> policySeams,
    String description) {
  /** Validates one published extension-surface fact family. */
  public ExtensionSurfaceFacts {
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(defaultPolicyPackId, "defaultPolicyPackId");
    implementedSeams = List.copyOf(Objects.requireNonNull(implementedSeams, "implementedSeams"));
    policySeams = List.copyOf(Objects.requireNonNull(policySeams, "policySeams"));
    Objects.requireNonNull(description, "description");
    List<String> implementedPolicySeamIds =
        policySeams.stream()
            .filter(seam -> seam.status() == CapabilityStatus.IMPLEMENTED)
            .map(PolicySeamFacts::seamId)
            .toList();
    if (!implementedPolicySeamIds.equals(implementedSeams)) {
      throw new IllegalArgumentException(
          "implementedSeams must equal the implemented policy-seam inventory.");
    }
  }
}
