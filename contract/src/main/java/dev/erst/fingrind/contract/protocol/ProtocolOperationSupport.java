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
    String usage = "fingrind " + id.wireName();
    if (!options.isEmpty()) {
      usage =
          usage
              + " "
              + options.stream()
                  .map(ProtocolOperationSupport::usageOption)
                  .collect(java.util.stream.Collectors.joining(" "));
    }
    return new ProtocolOperation(
        id,
        category,
        displayLabel,
        aliases,
        options,
        executionMode,
        usage,
        analysisSummary,
        examples);
  }

  private static String usageOption(String option) {
    return option.equals(ProtocolOptions.currentPassphraseSourceSyntax())
            || option.equals(ProtocolOptions.replacementPassphraseSourceSyntax())
        ? "[" + option + "]"
        : option;
  }
}
