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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the deterministic per-command descriptor baseline consumed by release-smoke field
 * coverage.
 */
final class ProtocolCapabilityBaseline {
  static final int SCHEMA_VERSION = 3;
  private static final Path INDEX_PATH = Path.of("index.json");
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

  /** Returns canonical relative resource paths and JSON documents for the complete baseline. */
  static Map<Path, String> renderedDocuments() {
    return renderedDocuments(snapshot());
  }

  /** Synchronizes the complete baseline directory, removing stale generated JSON fragments. */
  static void sync(Path baselineDirectory) throws IOException {
    Path targetDirectory = requireDirectory(baselineDirectory);
    Map<Path, String> documents = renderedDocuments();
    Files.createDirectories(targetDirectory);
    removeStaleFragments(targetDirectory, documents.keySet());
    for (Map.Entry<Path, String> document : documents.entrySet()) {
      Path target = targetPath(targetDirectory, document.getKey());
      Path parent = Objects.requireNonNull(target.getParent());
      Files.createDirectories(parent);
      if (!Files.isRegularFile(target) || !document.getValue().equals(Files.readString(target))) {
        Files.writeString(target, document.getValue());
      }
    }
  }

  /** Resolves one rendered document only when it remains contained by the generated directory. */
  private static Path targetPath(Path targetDirectory, Path relativeDocumentPath) {
    Path target = targetDirectory.resolve(relativeDocumentPath).normalize();
    if (!target.startsWith(targetDirectory)) {
      throw new IllegalArgumentException(
          "Capability baseline document path escapes its directory.");
    }
    return target;
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

  private static Map<Path, String> renderedDocuments(CapabilityBaselineSnapshot snapshot) {
    Map<Path, String> documents = orderedMap();
    Map<String, List<String>> commandFiles = orderedMap();
    for (OperationCategory category : OperationCategory.values()) {
      List<CapabilityBaselineCommand> commands =
          snapshot.commands().stream()
              .filter(command -> command.category().equals(category.wireValue()))
              .toList();
      List<String> categoryFiles =
          commands.stream().map(command -> commandPath(category, command)).toList();
      commandFiles.put(category.wireValue(), categoryFiles);
      for (CapabilityBaselineCommand command : commands) {
        Path path = Path.of(commandPath(category, command));
        documents.put(path, render(commandDocument(category, command)));
      }
    }
    documents.put(INDEX_PATH, render(indexDocument(snapshot, commandFiles)));
    return documents;
  }

  private static Path requireDirectory(Path baselineDirectory) {
    Path targetDirectory =
        Objects.requireNonNull(baselineDirectory, "baselineDirectory").toAbsolutePath().normalize();
    if (targetDirectory.getParent() == null) {
      throw new IllegalArgumentException(
          "Capability baseline directory must not be a filesystem root.");
    }
    return targetDirectory;
  }

  private static void removeStaleFragments(Path targetDirectory, Set<Path> expected)
      throws IOException {
    try (Stream<Path> paths = Files.walk(targetDirectory)) {
      List<Path> staleFragments =
          paths
              .filter(Files::isRegularFile)
              .map(targetDirectory::relativize)
              .filter(path -> path.toString().endsWith(".json"))
              .filter(path -> !expected.contains(path))
              .toList();
      for (Path staleFragment : staleFragments) {
        Files.delete(targetDirectory.resolve(staleFragment));
      }
    }
  }

  private static String commandPath(OperationCategory category, CapabilityBaselineCommand command) {
    return "commands/" + category.wireValue() + "/" + command.name() + ".json";
  }

  private static Map<String, Object> indexDocument(
      CapabilityBaselineSnapshot snapshot, Map<String, List<String>> commandFiles) {
    Map<String, Object> document = orderedMap();
    document.put("schemaVersion", snapshot.schemaVersion());
    document.put("commandFiles", commandFiles);
    return document;
  }

  private static Map<String, Object> commandDocument(
      OperationCategory category, CapabilityBaselineCommand command) {
    Map<String, Object> document = orderedMap();
    document.put("schemaVersion", SCHEMA_VERSION);
    document.put("category", category.wireValue());
    document.put("command", command.asJsonObject());
    return document;
  }

  private static String render(Map<String, Object> document) {
    return JSON_WRITER.writeValueAsString(document) + "\n";
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
