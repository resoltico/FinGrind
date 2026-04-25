package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** One core-owned operation descriptor used to render CLI help and capabilities. */
public record ProtocolOperation(
    OperationId id,
    OperationCategory category,
    ProtocolCommandSignature signature,
    ProtocolOperationOutputs outputs,
    ProtocolOperationDocumentation documentation) {
  /** Validates and defensively copies one protocol operation descriptor. */
  public ProtocolOperation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(signature, "signature");
    Objects.requireNonNull(outputs, "outputs");
    Objects.requireNonNull(documentation, "documentation");
  }

  /** Returns the public display label for this operation. */
  public String displayLabel() {
    return signature.displayLabel();
  }

  /** Returns the supported command aliases for this operation. */
  public List<String> aliases() {
    return signature.aliases();
  }

  /** Returns the supported CLI options for this operation. */
  public List<String> options() {
    return signature.options();
  }

  /** Returns the canonical usage string for this operation. */
  public String usage() {
    return signature.usage();
  }

  /** Returns the execution mode contract for this operation. */
  public ExecutionMode executionMode() {
    return outputs.executionMode();
  }

  /** Returns the supported output modes for this operation. */
  public List<OutputMode> outputModes() {
    return outputs.outputModes();
  }

  /** Returns the non-stdout artifact outputs for this operation. */
  public List<ProtocolArtifactOutput> artifactOutputs() {
    return outputs.artifactOutputs();
  }

  /** Returns the concise analysis summary for this operation. */
  public String analysisSummary() {
    return documentation.analysisSummary();
  }

  /** Returns the catalog examples for this operation. */
  public List<String> examples() {
    return documentation.examples();
  }
}
