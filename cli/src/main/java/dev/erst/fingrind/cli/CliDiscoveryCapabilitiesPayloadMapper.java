package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDiscoveryJsonModels;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Maps executable capability descriptors into compact and full CLI JSON discovery payloads. */
final class CliDiscoveryCapabilitiesPayloadMapper {
  private CliDiscoveryCapabilitiesPayloadMapper() {}

  static ProtocolSuccessPayload capabilitiesPayload(
      CapabilitiesDescriptor capabilitiesDescriptor, DiscoveryDetail detail) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    Objects.requireNonNull(detail, "detail");
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
    List<String> requestFileCommands = capabilitiesDescriptor.requestInput().requestFileCommands();
    return new CliDiscoveryJsonModels.CapabilitiesCompactPayload(
        capabilitiesDescriptor.application(),
        capabilitiesDescriptor.version(),
        DiscoveryDetail.COMPACT,
        capabilitiesDescriptor.storage().bookBoundary(),
        capabilitiesDescriptor.storage().engines().stream().map(Object::toString).toList(),
        requestInputCompactPayload(capabilitiesDescriptor),
        capabilitiesDescriptor.commands().allCommands().stream()
            .map(command -> commandSurfacePayload(command, requestFileCommands))
            .toList(),
        "Run '"
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

  private static String executionModeWireValue(CommandDescriptor command) {
    return switch (command.executionMode()) {
      case JSON_ENVELOPE -> "json-envelope";
      case RAW_JSON -> "raw-json";
    };
  }

  private static String commandCategory(CommandDescriptor command) {
    return dev.erst.fingrind.contract.protocol.ProtocolCatalog.operation(command.name())
        .category()
        .name()
        .toLowerCase(Locale.ROOT);
  }
}
