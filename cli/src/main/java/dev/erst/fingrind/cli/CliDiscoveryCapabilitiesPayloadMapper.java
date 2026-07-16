package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDiscoveryCapabilitiesSliceJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryCommonJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryRequestInputSliceJsonModels;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
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
              new CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesStorageSlicePayload(
                  capabilitiesDescriptor.storage()),
              storageHints());
      case REQUEST_INPUT ->
          focusedSlicePayload(
              capabilitiesDescriptor,
              detail,
              selections.focus(),
              selections.category(),
              new CliDiscoveryRequestInputSliceJsonModels.CapabilitiesRequestInputSlicePayload(
                  CliDiscoveryCapabilitiesOverviewPayloadMapper.requestInputCompactPayload(
                      capabilitiesDescriptor),
                  detail == DiscoveryDetail.FULL ? capabilitiesDescriptor.requestInput() : null),
              requestInputHints());
      case CURRENCY_MODEL ->
          focusedSlicePayload(
              capabilitiesDescriptor,
              detail,
              selections.focus(),
              selections.category(),
              new CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesCurrencySlicePayload(
                  capabilitiesDescriptor.currencyModel()),
              focusHints(detail));
      case BOOKKEEPING_KERNEL ->
          focusedSlicePayload(
              capabilitiesDescriptor,
              detail,
              selections.focus(),
              selections.category(),
              new CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesKernelSlicePayload(
                  capabilitiesDescriptor.bookkeepingKernel()),
              focusHints(detail));
      case CAPABILITY_CATALOG ->
          focusedSlicePayload(
              capabilitiesDescriptor,
              detail,
              selections.focus(),
              selections.category(),
              new CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesCatalogSlicePayload(
                  capabilitiesDescriptor.capabilityCatalog()),
              capabilityCatalogHints());
      case RESPONSE_CONTRACT ->
          focusedSlicePayload(
              capabilitiesDescriptor,
              detail,
              selections.focus(),
              selections.category(),
              responseContractSlicePayload(capabilitiesDescriptor, detail),
              focusHints(detail));
    };
  }

  private static ProtocolSuccessPayload overviewPayload(
      CapabilitiesDescriptor capabilitiesDescriptor, DiscoveryDetail detail) {
    return switch (detail) {
      case MINIMAL ->
          CliDiscoveryCapabilitiesOverviewPayloadMapper.minimalPayload(capabilitiesDescriptor);
      case COMPACT ->
          CliDiscoveryCapabilitiesOverviewPayloadMapper.compactPayload(capabilitiesDescriptor);
      case FULL ->
          CliDiscoveryCapabilitiesOverviewPayloadMapper.fullPayload(capabilitiesDescriptor);
    };
  }

  private static CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload
      focusedSlicePayload(
          CapabilitiesDescriptor capabilitiesDescriptor,
          DiscoveryDetail detail,
          DiscoveryFocus focus,
          @Nullable OperationCategory category,
          ProtocolSuccessPayload data,
          List<String> nextHints) {
    return new CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload(
        capabilitiesDescriptor.application(),
        capabilitiesDescriptor.version(),
        capabilitiesDescriptor.protocolVersion(),
        detail,
        focus,
        category == null ? null : category.wireValue(),
        data,
        nextHints);
  }

  private static CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload commandsSlicePayload(
      CapabilitiesDescriptor capabilitiesDescriptor,
      DiscoveryDetail detail,
      @Nullable OperationCategory category) {
    List<CommandDescriptor> filteredCommands =
        filteredCommands(capabilitiesDescriptor.commands(), category);
    return switch (detail) {
      case MINIMAL ->
          new CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload(
              category == null ? null : category.wireValue(),
              filteredCommands.stream()
                  .map(
                      command ->
                          new CliDiscoveryCommonJsonModels.CommandNamePayload(
                              command.name(),
                              ProtocolCatalog.operation(command.name()).category().wireValue()))
                  .toList(),
              null,
              null);
      case COMPACT ->
          new CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload(
              category == null ? null : category.wireValue(),
              filteredCommands.stream()
                  .map(
                      command ->
                          new CliDiscoveryCommonJsonModels.CommandNamePayload(
                              command.name(),
                              ProtocolCatalog.operation(command.name()).category().wireValue()))
                  .toList(),
              commandSurfacePayloads(
                  filteredCommands, capabilitiesDescriptor.requestInput().requestFileCommands()),
              null);
      case FULL ->
          new CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload(
              category == null ? null : category.wireValue(),
              filteredCommands.stream()
                  .map(
                      command ->
                          new CliDiscoveryCommonJsonModels.CommandNamePayload(
                              command.name(),
                              ProtocolCatalog.operation(command.name()).category().wireValue()))
                  .toList(),
              null,
              filteredCommands);
    };
  }

  static List<CliDiscoveryCommonJsonModels.CommandSurfacePayload> commandSurfacePayloads(
      List<CommandDescriptor> commands, List<String> requestFileCommands) {
    return commands.stream()
        .map(command -> commandSurfacePayload(command, requestFileCommands))
        .toList();
  }

  private static CliDiscoveryCommonJsonModels.CommandSurfacePayload commandSurfacePayload(
      CommandDescriptor command, List<String> requestFileCommands) {
    return new CliDiscoveryCommonJsonModels.CommandSurfacePayload(
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
        "Rerun with --detail compact for options and outputs, or --detail full for full command"
            + " descriptors.");
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
            + "' for a placeholder-first request scaffold.");
  }

  private static List<String> capabilityCatalogHints() {
    return List.of(
        "Each entry is a canonical published scope fact; partial entries carry their operative boundary.");
  }

  private static ProtocolSuccessPayload responseContractSlicePayload(
      CapabilitiesDescriptor capabilitiesDescriptor, DiscoveryDetail detail) {
    return switch (detail) {
      case MINIMAL ->
          new CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesResponseContractSummaryPayload(
              List.of(
                  capabilitiesDescriptor.responseModel().successStatus().wireValue(),
                  capabilitiesDescriptor.responseModel().rejectionStatus().wireValue(),
                  capabilitiesDescriptor.responseModel().errorStatus().wireValue()),
              capabilitiesDescriptor.preflight().semantics(),
              capabilitiesDescriptor.planExecution().journal(),
              capabilitiesDescriptor.reversals().model(),
              capabilitiesDescriptor.audit().requestProvenanceFields().size(),
              capabilitiesDescriptor.audit().committedFields().size());
      case COMPACT ->
          new CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesResponseContractCompactPayload(
              capabilitiesDescriptor.responseModel(),
              capabilitiesDescriptor.preflight().semantics(),
              capabilitiesDescriptor.planExecution().journal(),
              capabilitiesDescriptor.reversals().model(),
              capabilitiesDescriptor.audit().requestProvenanceFields().size(),
              capabilitiesDescriptor.audit().committedFields().size());
      case FULL ->
          new CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesResponseContractSlicePayload(
              capabilitiesDescriptor.responseModel(),
              capabilitiesDescriptor.planExecution(),
              capabilitiesDescriptor.audit(),
              capabilitiesDescriptor.accountRegistry(),
              capabilitiesDescriptor.reversals(),
              capabilitiesDescriptor.preflight());
    };
  }

  private static List<String> focusHints(DiscoveryDetail detail) {
    return detail == DiscoveryDetail.FULL
        ? List.of("This slice is the exhaustive descriptor surface for the selected focus.")
        : List.of("Rerun with --detail full only when you need the exhaustive descriptor surface.");
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
