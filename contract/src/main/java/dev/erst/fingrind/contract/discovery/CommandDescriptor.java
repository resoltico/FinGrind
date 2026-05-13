package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.List;

/** Descriptor for one advertised CLI command. */
public record CommandDescriptor(
    OperationId name,
    List<String> aliases,
    List<String> options,
    ExecutionMode executionMode,
    List<OutputMode> outputModes,
    List<ArtifactOutputDescriptor> artifactOutputs,
    String summary)
    implements ContractDiscoveryDescriptor {
  /** Validates one command descriptor payload. */
  public CommandDescriptor {
    name = ContractDescriptorValidation.requireValue(name, "name");
    aliases = ContractDescriptorValidation.copyList(aliases, "aliases");
    options = ContractDescriptorValidation.copyList(options, "options");
    executionMode = ContractDescriptorValidation.requireValue(executionMode, "executionMode");
    outputModes = ContractDescriptorValidation.copyList(outputModes, "outputModes");
    artifactOutputs = ContractDescriptorValidation.copyList(artifactOutputs, "artifactOutputs");
    summary = ContractDescriptorValidation.requireText(summary, "summary");
  }

  /** Returns the human-facing stdout contract summary used by CLI help text. */
  public String stdoutContractSummary() {
    if (!outputModes.isEmpty()) {
      return String.join(", ", outputModes.stream().map(OutputMode::wireValue).toList())
          + " (via --output)";
    }
    return switch (executionMode) {
      case JSON_ENVELOPE -> "json envelope (fixed)";
      case RAW_JSON -> "raw json (fixed)";
    };
  }
}
