package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** One core-owned operation descriptor used to render CLI help and capabilities. */
public record ProtocolOperation(
    OperationId id,
    OperationCategory category,
    String displayLabel,
    List<String> aliases,
    List<String> options,
    ExecutionMode executionMode,
    List<String> outputModes,
    List<ProtocolArtifactOutput> artifactOutputs,
    String usage,
    String analysisSummary,
    List<String> examples) {
  /** Validates and defensively copies one protocol operation descriptor. */
  public ProtocolOperation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(category, "category");
    displayLabel = requireText(displayLabel, "displayLabel");
    aliases = copyList(aliases);
    options = copyList(options);
    Objects.requireNonNull(executionMode, "executionMode");
    outputModes = copyList(outputModes);
    artifactOutputs = copyList(artifactOutputs);
    usage = requireText(usage, "usage");
    analysisSummary = requireText(analysisSummary, "analysisSummary");
    examples = copyList(examples);
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }

  private static <T> List<T> copyList(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
