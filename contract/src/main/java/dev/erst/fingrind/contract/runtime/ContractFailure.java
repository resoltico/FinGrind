package dev.erst.fingrind.contract.runtime;

import static dev.erst.fingrind.contract.internal.ContractDescriptorValidation.requireOptionalText;
import static dev.erst.fingrind.contract.internal.ContractDescriptorValidation.requireText;
import static dev.erst.fingrind.contract.internal.ContractDescriptorValidation.requireValue;

import dev.erst.fingrind.core.ArtifactPublicationRetention;
import org.jspecify.annotations.Nullable;

/** One deterministic contract-owned failure with the canonical machine descriptor and hints. */
public record ContractFailure(
    ContractErrors.Descriptor descriptor,
    String message,
    @Nullable String hint,
    @Nullable String argument,
    @Nullable ContractFailurePaths paths,
    @Nullable ContractFailureDetails details,
    @Nullable ArtifactPublicationRetention retainedStage) {
  /** Validates one deterministic contract failure payload. */
  public ContractFailure {
    descriptor = requireValue(descriptor, "descriptor");
    message = requireText(message, "message");
    hint = requireOptionalText(hint, "hint");
    argument = requireOptionalText(argument, "argument");
    details = ContractFailureDetailRequirements.requireCompatible(descriptor, details);
  }

  /** Returns the stable machine-readable failure code for this deterministic contract failure. */
  public String code() {
    return descriptor.code();
  }
}
