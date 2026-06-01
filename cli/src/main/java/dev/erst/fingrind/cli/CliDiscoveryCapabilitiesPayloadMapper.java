package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDiscoveryJsonModels;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Maps executable capability descriptors into compact and full CLI JSON discovery payloads. */
final class CliDiscoveryCapabilitiesPayloadMapper {
  private CliDiscoveryCapabilitiesPayloadMapper() {}

  static ProtocolSuccessPayload capabilitiesPayload(
      CapabilitiesDescriptor capabilitiesDescriptor,
      DiscoveryDetail detail,
      CliDiscoverySelections selections) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    Objects.requireNonNull(detail, "detail");
    Objects.requireNonNull(selections, "selections");
    return switch (selections.focus()) {
      case OVERVIEW -> overviewPayload(capabilitiesDescriptor, detail);
      case COMMANDS ->
          focusedSlicePayload(
              capabilitiesDescriptor,
              detail,
              selections.focus(),
              selections.category(),
              commandsSlicePayload(capabilitiesDescriptor, detail, selections.category()),
              commandHints());
      case STORAGE ->
          focusedSlicePayload(
              capabilitiesDescriptor,
              detail,
              selections.focus(),
              selections.category(),
              new CliDiscoveryJsonModels.CapabilitiesStorageSlicePayload(
                  capabilitiesDescriptor.storage()),
              storageHints());
      case REQUEST_INPUT ->
          focusedSlicePayload(
              capabilitiesDescriptor,
              detail,
              selections.focus(),
              selections.category(),
              new CliDiscoveryJsonModels.CapabilitiesRequestInputSlicePayload(
                  requestInputCompactPayload(capabilitiesDescriptor),
                  detail == DiscoveryDetail.FULL ? capabilitiesDescriptor.requestInput() : null),
              requestInputHints());
      case CURRENCY_MODEL ->
          focusedSlicePayload(
              capabilitiesDescriptor,
              detail,
              selections.focus(),
              selections.category(),
              new CliDiscoveryJsonModels.CapabilitiesCurrencySlicePayload(
                  capabilitiesDescriptor.currencyModel()),
              fullDescriptorHints());
      case BOOKKEEPING_KERNEL ->
          focusedSlicePayload(
              capabilitiesDescriptor,
              detail,
              selections.focus(),
              selections.category(),
              new CliDiscoveryJsonModels.CapabilitiesKernelSlicePayload(
                  capabilitiesDescriptor.bookkeepingKernel()),
              fullDescriptorHints());
      case RESPONSE_CONTRACT ->
          focusedSlicePayload(
              capabilitiesDescriptor,
              detail,
              selections.focus(),
              selections.category(),
              new CliDiscoveryJsonModels.CapabilitiesResponseContractSlicePayload(
                  capabilitiesDescriptor.responseModel(),
                  capabilitiesDescriptor.planExecution(),
                  capabilitiesDescriptor.audit(),
                  capabilitiesDescriptor.accountRegistry(),
                  capabilitiesDescriptor.reversals(),
                  capabilitiesDescriptor.preflight()),
              fullDescriptorHints());
    };
  }

  private static ProtocolSuccessPayload overviewPayload(
      CapabilitiesDescriptor capabilitiesDescriptor, DiscoveryDetail detail) {
    return switch (detail) {
      case MINIMAL -> minimalCapabilitiesPayload(capabilitiesDescriptor);
      case COMPACT -> compactCapabilitiesPayload(capabilitiesDescriptor);
      case FULL -> fullCapabilitiesPayload(capabilitiesDescriptor);
    };
  }

  private static CliDiscoveryJsonModels.CapabilitiesMinimalPayload minimalCapabilitiesPayload(
      CapabilitiesDescriptor capabilitiesDescriptor) {
    return new CliDiscoveryJsonModels.CapabilitiesMinimalPayload(
        capabilitiesDescriptor.application(),
        capabilitiesDescriptor.version(),
        DiscoveryDetail.MINIMAL,
        DiscoveryFocus.OVERVIEW,
        capabilitiesDescriptor.bookkeepingKernel().scope(),
        capabilitiesDescriptor.bookkeepingKernel().builtInStatements(),
        capabilitiesDescriptor.storage().bookBoundary(),
        capabilitiesDescriptor.currencyModel().scope(),
        capabilitiesDescriptor.currencyModel().multiCurrencyStatus(),
        requestInputCompactPayload(capabilitiesDescriptor),
        "Rerun with '"
            + CliInvocationText.commandExample(OperationId.CAPABILITIES)
            + " --output json "
            + ProtocolOptions.DETAIL
            + " compact' for stable command, storage, and request-entry discovery.",
        "Rerun with '"
            + CliInvocationText.commandExample(OperationId.CAPABILITIES)
            + " --output json "
            + ProtocolOptions.DETAIL
            + " full' for the exhaustive schema and response contract.");
  }

  private static CliDiscoveryJsonModels.CapabilitiesCompactPayload compactCapabilitiesPayload(
      CapabilitiesDescriptor capabilitiesDescriptor) {
    return new CliDiscoveryJsonModels.CapabilitiesCompactPayload(
        capabilitiesDescriptor.application(),
        capabilitiesDescriptor.version(),
        DiscoveryDetail.COMPACT,
        DiscoveryFocus.OVERVIEW,
        capabilitiesDescriptor.storage().bookBoundary(),
        capabilitiesDescriptor.storage().engines().stream().map(Object::toString).toList(),
        requestInputCompactPayload(capabilitiesDescriptor),
        commandCounts(capabilitiesDescriptor.commands()),
        "Run '"
            + CliInvocationText.commandExample(OperationId.CAPABILITIES)
            + " --output json "
            + ProtocolOptions.FOCUS
            + " commands' for command-family retrieval, or rerun with '"
            + CliInvocationText.commandExample(OperationId.CAPABILITIES)
            + " --output json "
            + ProtocolOptions.DETAIL
            + " full' for exhaustive schema, response, and doctrine details.");
  }

  private static CliDiscoveryJsonModels.CapabilitiesPayload fullCapabilitiesPayload(
      CapabilitiesDescriptor capabilitiesDescriptor) {
    return new CliDiscoveryJsonModels.CapabilitiesPayload(
        capabilitiesDescriptor.application(),
        capabilitiesDescriptor.version(),
        DiscoveryDetail.FULL,
        DiscoveryFocus.OVERVIEW,
        capabilitiesDescriptor.storage(),
        capabilitiesDescriptor.commands(),
        capabilitiesDescriptor.requestInput(),
        List.of(
            "Use compact detail for stable command, storage, and request-entry discovery.",
            "Rerun with '"
                + CliInvocationText.commandExample(OperationId.CAPABILITIES)
                + " --output json "
                + ProtocolOptions.DETAIL
                + " full' when you need the exhaustive schema and response contract."),
        capabilitiesDescriptor);
  }

  private static CliDiscoveryJsonModels.CapabilitiesSlicePayload focusedSlicePayload(
      CapabilitiesDescriptor capabilitiesDescriptor,
      DiscoveryDetail detail,
      DiscoveryFocus focus,
      @Nullable OperationCategory category,
      ProtocolSuccessPayload data,
      List<String> nextHints) {
    return new CliDiscoveryJsonModels.CapabilitiesSlicePayload(
        capabilitiesDescriptor.application(),
        capabilitiesDescriptor.version(),
        detail,
        focus,
        category == null ? null : category.wireValue(),
        data,
        nextHints);
  }

  private static CliDiscoveryJsonModels.CapabilitiesCommandsSlicePayload commandsSlicePayload(
      CapabilitiesDescriptor capabilitiesDescriptor,
      DiscoveryDetail detail,
      @Nullable OperationCategory category) {
    List<CommandDescriptor> filteredCommands =
        filteredCommands(capabilitiesDescriptor.commands(), category);
    return switch (detail) {
      case MINIMAL ->
          new CliDiscoveryJsonModels.CapabilitiesCommandsSlicePayload(
              category == null ? null : category.wireValue(),
              filteredCommands.stream()
                  .map(
                      command ->
                          new CliDiscoveryJsonModels.CommandNamePayload(
                              command.name(),
                              ProtocolCatalog.operation(command.name()).category().wireValue()))
                  .toList(),
              null,
              null);
      case COMPACT ->
          new CliDiscoveryJsonModels.CapabilitiesCommandsSlicePayload(
              category == null ? null : category.wireValue(),
              filteredCommands.stream()
                  .map(
                      command ->
                          new CliDiscoveryJsonModels.CommandNamePayload(
                              command.name(),
                              ProtocolCatalog.operation(command.name()).category().wireValue()))
                  .toList(),
              filteredCommands.stream()
                  .map(
                      command ->
                          commandSurfacePayload(
                              command, capabilitiesDescriptor.requestInput().requestFileCommands()))
                  .toList(),
              null);
      case FULL ->
          new CliDiscoveryJsonModels.CapabilitiesCommandsSlicePayload(
              category == null ? null : category.wireValue(),
              filteredCommands.stream()
                  .map(
                      command ->
                          new CliDiscoveryJsonModels.CommandNamePayload(
                              command.name(),
                              ProtocolCatalog.operation(command.name()).category().wireValue()))
                  .toList(),
              null,
              filteredCommands);
    };
  }

  private static CliDiscoveryJsonModels.CommandSurfacePayload commandSurfacePayload(
      CommandDescriptor command, List<String> requestFileCommands) {
    return new CliDiscoveryJsonModels.CommandSurfacePayload(
        command.name(),
        commandCategory(command),
        command.summary(),
        command.aliases(),
        command.options(),
        executionModeWireValue(command),
        command.outputModes().stream().map(outputMode -> outputMode.wireValue()).toList(),
        command.selectableOutputDefaults(),
        command.artifactOutputs().stream()
            .map(artifact -> artifact.format() + " via " + artifact.option())
            .toList(),
        requestFileCommands.contains(command.name().wireName()));
  }

  private static CliDiscoveryJsonModels.RequestInputCompactPayload requestInputCompactPayload(
      CapabilitiesDescriptor capabilitiesDescriptor) {
    return new CliDiscoveryJsonModels.RequestInputCompactPayload(
        capabilitiesDescriptor.requestInput().bookFileOption(),
        capabilitiesDescriptor.requestInput().bookPassphraseOptions(),
        capabilitiesDescriptor.requestInput().requestFileOption(),
        capabilitiesDescriptor.requestInput().requestFileCommands(),
        capabilitiesDescriptor.requestInput().stdinToken(),
        capabilitiesDescriptor.requestInput().outputOption());
  }

  private static List<CliDiscoveryJsonModels.CommandCountPayload> commandCounts(
      CommandCatalogDescriptor commands) {
    return List.of(
        new CliDiscoveryJsonModels.CommandCountPayload("discovery", commands.discovery().size()),
        new CliDiscoveryJsonModels.CommandCountPayload(
            "administration", commands.administration().size()),
        new CliDiscoveryJsonModels.CommandCountPayload("query", commands.query().size()),
        new CliDiscoveryJsonModels.CommandCountPayload("write", commands.write().size()));
  }

  private static List<CommandDescriptor> filteredCommands(
      CommandCatalogDescriptor commands, @Nullable OperationCategory category) {
    if (category == null) {
      return commands.allCommands();
    }
    return switch (category) {
      case DISCOVERY -> commands.discovery();
      case ADMINISTRATION -> commands.administration();
      case QUERY -> commands.query();
      case WRITE -> commands.write();
    };
  }

  private static List<String> commandHints() {
    return List.of(
        "Use --category <discovery|administration|query|write> to narrow command families.",
        "Rerun with --detail compact for options and outputs, or --detail full for full command descriptors.");
  }

  private static List<String> storageHints() {
    return List.of(
        "Run '"
            + CliInvocationText.commandExample(OperationId.ENVIRONMENT)
            + " --output json' for live runtime and SQLite loading facts.");
  }

  private static List<String> requestInputHints() {
    return List.of(
        "Run '"
            + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
            + "' for a runnable request scaffold.");
  }

  private static List<String> fullDescriptorHints() {
    return List.of(
        "Rerun with --detail full only when you need the exhaustive descriptor surface.");
  }

  private static String executionModeWireValue(CommandDescriptor command) {
    return switch (command.executionMode()) {
      case JSON_ENVELOPE -> "json-envelope";
      case RAW_JSON -> "raw-json";
    };
  }

  private static String commandCategory(CommandDescriptor command) {
    return dev.erst.fingrind.contract.protocol.ProtocolCatalog.operation(command.name())
        .category()
        .wireValue();
  }
}
