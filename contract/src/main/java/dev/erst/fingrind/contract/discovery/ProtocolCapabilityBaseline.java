package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the deterministic command-descriptor baseline consumed by release-smoke field coverage.
 */
final class ProtocolCapabilityBaseline {
  static final int SCHEMA_VERSION = 2;

  private static final ObjectWriter JSON_WRITER =
      JsonMapper.builder().build().writerWithDefaultPrettyPrinter();

  private ProtocolCapabilityBaseline() {}

  /** Returns the complete descriptor baseline in canonical protocol-catalog order. */
  static CapabilityBaselineSnapshot snapshot() {
    Map<OperationId, CommandDescriptor> descriptorsByOperation =
        MachineContractDomainDescriptors.commandCatalog().allCommands().stream()
            .collect(Collectors.toMap(CommandDescriptor::name, descriptor -> descriptor));
    return projectSnapshot(ProtocolCatalog.operations(), descriptorsByOperation);
  }

  private static CapabilityBaselineSnapshot projectSnapshot(
      List<ProtocolOperation> operations,
      Map<OperationId, CommandDescriptor> descriptorsByOperation) {
    List<CapabilityBaselineCommand> commands =
        operations.stream()
            .map(
                operation ->
                    command(operation, requireDescriptor(descriptorsByOperation, operation.id())))
            .toList();
    if (commands.size() != descriptorsByOperation.size()) {
      throw new IllegalStateException(
          "Machine-contract command descriptors do not cover the canonical protocol catalog.");
    }
    return new CapabilityBaselineSnapshot(SCHEMA_VERSION, commands);
  }

  /** Renders the baseline as stable, pretty-printed JSON with one trailing newline. */
  static String render() {
    return JSON_WRITER.writeValueAsString(jsonDocument(snapshot())) + "\n";
  }

  /**
   * Writes the baseline only when the selected snapshot path differs from the canonical rendering.
   */
  static void sync(Path snapshotPath) throws IOException {
    Path target = Objects.requireNonNull(snapshotPath, "snapshotPath").toAbsolutePath().normalize();
    Path parent =
        Objects.requireNonNull(
            target.getParent(), "capability baseline snapshot path must have a parent");
    Files.createDirectories(parent);
    String rendered = render();
    if (!Files.isRegularFile(target) || !rendered.equals(Files.readString(target))) {
      Files.writeString(target, rendered);
    }
  }

  private static CommandDescriptor requireDescriptor(
      Map<OperationId, CommandDescriptor> descriptorsByOperation, OperationId operationId) {
    CommandDescriptor descriptor = descriptorsByOperation.get(operationId);
    if (descriptor == null) {
      throw new IllegalStateException(
          "Machine-contract command descriptor is missing canonical operation "
              + operationId.wireName()
              + ".");
    }
    return descriptor;
  }

  private static CapabilityBaselineCommand command(
      ProtocolOperation operation, CommandDescriptor descriptor) {
    if (operation.id() != descriptor.name()) {
      throw new IllegalStateException(
          "Machine-contract command descriptor does not match canonical operation "
              + operation.id().wireName()
              + ".");
    }
    return new CapabilityBaselineCommand(
        operation.category().wireValue(),
        operation.id().wireName(),
        descriptor.displayLabel(),
        operation.aliases(),
        operation.options(),
        operation.executionMode().wireValue(),
        operation.outputModes().stream().map(outputMode -> outputMode.wireValue()).toList(),
        selectableOutputDefaults(descriptor),
        operation.artifactOutputs().stream()
            .map(
                artifact ->
                    new CapabilityBaselineArtifact(
                        artifact.format(), artifact.option(), artifact.description()))
            .toList(),
        operation.analysisSummary());
  }

  private static @Nullable CapabilityBaselineSelectableOutputDefaults selectableOutputDefaults(
      CommandDescriptor descriptor) {
    @Nullable SelectableOutputDefaultsDescriptor defaults = descriptor.selectableOutputDefaults();
    if (defaults == null) {
      return null;
    }
    return new CapabilityBaselineSelectableOutputDefaults(
        defaults.interactiveTerminal().wireValue(), defaults.redirectedStdout().wireValue());
  }

  private static Map<String, Object> jsonDocument(CapabilityBaselineSnapshot snapshot) {
    Map<String, List<Map<String, Object>>> commandGroups = orderedMap();
    for (OperationCategory category : OperationCategory.values()) {
      List<Map<String, Object>> group =
          snapshot.commands().stream()
              .filter(command -> command.category().equals(category.wireValue()))
              .map(CapabilityBaselineCommand::asJsonObject)
              .toList();
      commandGroups.put(category.wireValue(), group);
    }
    Map<String, Object> document = orderedMap();
    document.put("schemaVersion", snapshot.schemaVersion());
    document.put("commands", commandGroups);
    return document;
  }

  record CapabilityBaselineSnapshot(int schemaVersion, List<CapabilityBaselineCommand> commands) {
    CapabilityBaselineSnapshot {
      if (schemaVersion != SCHEMA_VERSION) {
        throw new IllegalArgumentException("Unsupported capability baseline schema version.");
      }
      commands = List.copyOf(commands);
    }
  }

  record CapabilityBaselineCommand(
      String category,
      String name,
      String displayLabel,
      List<String> aliases,
      List<String> options,
      String executionMode,
      List<String> outputModes,
      @Nullable CapabilityBaselineSelectableOutputDefaults selectableOutputDefaults,
      List<CapabilityBaselineArtifact> artifactOutputs,
      String summary) {
    CapabilityBaselineCommand {
      category = requireText(category, "category");
      name = requireText(name, "name");
      displayLabel = requireText(displayLabel, "displayLabel");
      aliases = List.copyOf(aliases);
      options = List.copyOf(options);
      executionMode = requireText(executionMode, "executionMode");
      outputModes = List.copyOf(outputModes);
      artifactOutputs = List.copyOf(artifactOutputs);
      summary = requireText(summary, "summary");
    }

    Map<String, Object> asJsonObject() {
      Map<String, Object> command = orderedMap();
      command.put("name", name);
      command.put("displayLabel", displayLabel);
      command.put("aliases", aliases);
      command.put("options", options);
      command.put("executionMode", executionMode);
      command.put("outputModes", outputModes);
      if (selectableOutputDefaults != null) {
        command.put("selectableOutputDefaults", selectableOutputDefaults.asJsonObject());
      }
      command.put(
          "artifactOutputs",
          artifactOutputs.stream().map(CapabilityBaselineArtifact::asJsonObject).toList());
      command.put("summary", summary);
      return command;
    }
  }

  record CapabilityBaselineSelectableOutputDefaults(
      String interactiveTerminal, String redirectedStdout) {
    CapabilityBaselineSelectableOutputDefaults {
      interactiveTerminal = requireText(interactiveTerminal, "interactiveTerminal");
      redirectedStdout = requireText(redirectedStdout, "redirectedStdout");
    }

    Map<String, Object> asJsonObject() {
      Map<String, Object> defaults = orderedMap();
      defaults.put("interactiveTerminal", interactiveTerminal);
      defaults.put("redirectedStdout", redirectedStdout);
      return defaults;
    }
  }

  record CapabilityBaselineArtifact(String format, String option, String description) {
    CapabilityBaselineArtifact {
      format = requireText(format, "format");
      option = requireText(option, "option");
      description = requireText(description, "description");
    }

    Map<String, Object> asJsonObject() {
      Map<String, Object> artifact = orderedMap();
      artifact.put("format", format);
      artifact.put("option", option);
      artifact.put("description", description);
      return artifact;
    }
  }

  private static String requireText(String value, String label) {
    String normalized = Objects.requireNonNull(value, label).strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(label + " must not be blank.");
    }
    return normalized;
  }

  private static <K, V> Map<K, V> orderedMap() {
    return new LinkedHashMap<>();
  }
}
