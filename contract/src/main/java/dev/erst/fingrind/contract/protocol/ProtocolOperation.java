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
    String usage,
    String analysisSummary,
    List<String> examples) {
  /** Validates and defensively copies one protocol operation descriptor. */
  public ProtocolOperation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(displayLabel, "displayLabel");
    aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
    options = List.copyOf(Objects.requireNonNull(options, "options"));
    Objects.requireNonNull(executionMode, "executionMode");
    Objects.requireNonNull(usage, "usage");
    Objects.requireNonNull(analysisSummary, "analysisSummary");
    examples = List.copyOf(Objects.requireNonNull(examples, "examples"));
  }
}
