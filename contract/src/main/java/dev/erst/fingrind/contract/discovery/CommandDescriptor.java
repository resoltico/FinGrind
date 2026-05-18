package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Descriptor for one advertised CLI command. */
public record CommandDescriptor(
    OperationId name,
    List<String> aliases,
    List<String> options,
    ExecutionMode executionMode,
    List<OutputMode> outputModes,
    @Nullable SelectableOutputDefaultsDescriptor selectableOutputDefaults,
    List<ArtifactOutputDescriptor> artifactOutputs,
    String summary)
    implements ContractDiscoveryDescriptor {
  /** Convenience constructor that derives selectable-output defaults from the command id. */
  public CommandDescriptor(
      OperationId name,
      List<String> aliases,
      List<String> options,
      ExecutionMode executionMode,
      List<OutputMode> outputModes,
      List<ArtifactOutputDescriptor> artifactOutputs,
      String summary) {
    this(
        name,
        aliases,
        options,
        executionMode,
        outputModes,
        inferredSelectableOutputDefaults(name, outputModes),
        artifactOutputs,
        summary);
  }

  /** Validates one command descriptor payload. */
  public CommandDescriptor {
    name = ContractDescriptorValidation.requireValue(name, "name");
    aliases = ContractDescriptorValidation.copyList(aliases, "aliases");
    options = ContractDescriptorValidation.copyList(options, "options");
    executionMode = ContractDescriptorValidation.requireValue(executionMode, "executionMode");
    outputModes = ContractDescriptorValidation.copyList(outputModes, "outputModes");
    if (outputModes.isEmpty()) {
      if (selectableOutputDefaults != null) {
        throw new IllegalArgumentException(
            "selectableOutputDefaults must be absent when outputModes is empty.");
      }
    } else {
      Objects.requireNonNull(selectableOutputDefaults, "selectableOutputDefaults");
    }
    artifactOutputs = ContractDescriptorValidation.copyList(artifactOutputs, "artifactOutputs");
    summary = ContractDescriptorValidation.requireText(summary, "summary");
  }

  /** Returns the human-facing stdout contract summary used by CLI help text. */
  public String stdoutContractSummary() {
    if (!outputModes.isEmpty()) {
      SelectableOutputDefaultsDescriptor defaults =
          Objects.requireNonNull(selectableOutputDefaults, "selectableOutputDefaults");
      return String.join(", ", outputModes.stream().map(OutputMode::wireValue).toList())
          + " (via --output; default: "
          + defaults.interactiveTerminal().wireValue()
          + " interactive, "
          + defaults.redirectedStdout().wireValue()
          + " redirected)";
    }
    return switch (executionMode) {
      case JSON_ENVELOPE -> "json envelope (fixed)";
      case RAW_JSON -> "raw json (fixed)";
    };
  }

  private static @Nullable SelectableOutputDefaultsDescriptor inferredSelectableOutputDefaults(
      OperationId name, List<OutputMode> outputModes) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(outputModes, "outputModes");
    if (outputModes.isEmpty()) {
      return null;
    }
    return new SelectableOutputDefaultsDescriptor(OutputMode.HUMAN, OutputMode.JSON);
  }
}
