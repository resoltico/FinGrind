package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Descriptor for one advertised CLI command. */
public record CommandDescriptor(
    String name,
    List<String> aliases,
    List<String> options,
    String executionMode,
    List<String> outputModes,
    List<ArtifactOutputDescriptor> artifactOutputs,
    String summary) {
  /** Validates one command descriptor payload. */
  public CommandDescriptor {
    name = ContractDescriptorValidation.requireText(name, "name");
    aliases = ContractDescriptorValidation.copyList(aliases, "aliases");
    options = ContractDescriptorValidation.copyList(options, "options");
    executionMode = ContractDescriptorValidation.requireText(executionMode, "executionMode");
    outputModes = ContractDescriptorValidation.copyList(outputModes, "outputModes");
    artifactOutputs = ContractDescriptorValidation.copyList(artifactOutputs, "artifactOutputs");
    summary = ContractDescriptorValidation.requireText(summary, "summary");
  }
}
