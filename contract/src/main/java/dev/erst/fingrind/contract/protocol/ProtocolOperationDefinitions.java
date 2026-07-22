package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
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
      List<ProtocolExampleStep> exampleSteps) {
    return operation(
        new OperationDefinition(
            id,
            category,
            displayLabel,
            aliases,
            options,
            executionMode,
            List.of(),
            List.of(),
            analysisSummary,
            exampleSteps));
  }

  /** Builds the standard JSON-or-text operation surface used by ordinary protocol commands. */
  static ProtocolOperation jsonEnvelopeOperation(
      OperationId id,
      OperationCategory category,
      String displayLabel,
      List<String> options,
      String analysisSummary,
      List<ProtocolExampleStep> exampleSteps) {
    return operation(
        id,
        category,
        displayLabel,
        List.of(),
        options,
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        analysisSummary,
        exampleSteps);
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
      List<ProtocolExampleStep> exampleSteps) {
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
            exampleSteps));
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
        new ProtocolOperationDocumentation(
            definition.analysisSummary(), definition.exampleSteps()));
  }

  private static String usageOption(String option) {
    return option.equals(ProtocolOptions.currentPassphraseSourceSyntax())
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
      List<ProtocolExampleStep> exampleSteps) {
    OperationDefinition {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(category, "category");
      displayLabel = requireText(displayLabel, "displayLabel");
      aliases = ContractDescriptorValidation.copyList(aliases, "aliases");
      options = ContractDescriptorValidation.copyList(options, "options");
      Objects.requireNonNull(executionMode, "executionMode");
      outputModes = ContractDescriptorValidation.copyList(outputModes, "outputModes");
      artifactOutputs = ContractDescriptorValidation.copyList(artifactOutputs, "artifactOutputs");
      analysisSummary = requireText(analysisSummary, "analysisSummary");
      exampleSteps = ContractDescriptorValidation.copyList(exampleSteps, "exampleSteps");
    }
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }
}
