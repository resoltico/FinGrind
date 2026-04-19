package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Common builders for canonical protocol operation descriptors. */
final class ProtocolOperationSupport {
  private ProtocolOperationSupport() {}

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
            List.of(OutputMode.JSON.wireValue()),
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
      List<String> outputModes,
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
                  .map(ProtocolOperationSupport::usageOption)
                  .collect(java.util.stream.Collectors.joining(" "));
    }
    return new ProtocolOperation(
        definition.id(),
        definition.category(),
        definition.displayLabel(),
        definition.aliases(),
        definition.options(),
        definition.executionMode(),
        definition.outputModes(),
        definition.artifactOutputs(),
        usage,
        definition.analysisSummary(),
        definition.examples());
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
      List<String> outputModes,
      List<ProtocolArtifactOutput> artifactOutputs,
      String analysisSummary,
      List<String> examples) {}
}
