package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;
import java.util.Objects;

/** Output-contract facts for one canonical public protocol operation. */
public record ProtocolOperationOutputs(
    ExecutionMode executionMode,
    List<OutputMode> outputModes,
    List<ProtocolArtifactOutput> artifactOutputs) {
  /** Validates one operation-output descriptor. */
  public ProtocolOperationOutputs {
    Objects.requireNonNull(executionMode, "executionMode");
    outputModes = ContractDescriptorValidation.copyList(outputModes, "outputModes");
    artifactOutputs = ContractDescriptorValidation.copyList(artifactOutputs, "artifactOutputs");
  }
}
