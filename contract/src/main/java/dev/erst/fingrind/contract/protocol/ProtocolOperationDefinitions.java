package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Common builders for canonical protocol operation descriptors. */
final class ProtocolOperationDefinitions {
  private ProtocolOperationDefinitions() {}

  static ProtocolOperation operation(
      OperationId id,
      OperationCategory category,
      String displayLabel,
      List<String> aliases,
      List<String> options,
      ExecutionMode executionMode,
      String analysisSummary,
      List<String> examples) {
    return operation(
        new OperationDefinition(
            id,
            category,
            displayLabel,
            aliases,
            options,
            executionMode,
            List.of(OutputMode.JSON),
            List.of(),
            analysisSummary,
            examples));
  }

  static ProtocolOperation operation(
      OperationId id,
      OperationCategory category,
      String displayLabel,
      List<String> aliases,
      List<String> options,
      ExecutionMode executionMode,
      List<OutputMode> outputModes,
      String analysisSummary,
      List<String> examples) {
    return operation(
        new OperationDefinition(
            id,
            category,
            displayLabel,
            aliases,
            options,
            executionMode,
            outputModes,
            List.of(),
            analysisSummary,
            examples));
  }

  static ProtocolOperation operation(OperationDefinition definition) {
    String usage = "fingrind " + definition.id().wireName();
    if (!definition.options().isEmpty()) {
      usage =
          usage
              + " "
              + definition.options().stream()
                  .map(ProtocolOperationDefinitions::usageOption)
                  .collect(java.util.stream.Collectors.joining(" "));
    }
    return new ProtocolOperation(
        definition.id(),
        definition.category(),
        new ProtocolCommandSignature(
            definition.displayLabel(), definition.aliases(), definition.options(), usage),
        new ProtocolOperationOutputs(
            definition.executionMode(), definition.outputModes(), definition.artifactOutputs()),
        new ProtocolOperationDocumentation(definition.analysisSummary(), definition.examples()));
  }

  private static String usageOption(String option) {
    return option.equals(ProtocolOptions.currentPassphraseSourceSyntax())
            || option.equals(ProtocolOptions.replacementPassphraseSourceSyntax())
        ? "[" + option + "]"
        : option;
  }

  record OperationDefinition(
      OperationId id,
      OperationCategory category,
      String displayLabel,
      List<String> aliases,
      List<String> options,
      ExecutionMode executionMode,
      List<OutputMode> outputModes,
      List<ProtocolArtifactOutput> artifactOutputs,
      String analysisSummary,
      List<String> examples) {
    OperationDefinition {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(category, "category");
      displayLabel = requireText(displayLabel, "displayLabel");
      aliases = copyList(aliases);
      options = copyList(options);
      Objects.requireNonNull(executionMode, "executionMode");
      outputModes = copyList(outputModes);
      artifactOutputs = copyList(artifactOutputs);
      analysisSummary = requireText(analysisSummary, "analysisSummary");
      examples = copyList(examples);
    }
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
