package dev.erst.fingrind.contract.protocol;

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
    outputModes = copyList(outputModes);
    artifactOutputs = copyList(artifactOutputs);
  }

  private static <T> List<T> copyList(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
