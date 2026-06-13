package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDiscoveryCapabilitiesJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryCommonJsonModels;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;
import java.util.Objects;

/** Maps overview-level capabilities descriptors into minimal, compact, and full JSON payloads. */
final class CliDiscoveryCapabilitiesOverviewPayloadMapper {
  private CliDiscoveryCapabilitiesOverviewPayloadMapper() {}

  static CliDiscoveryCapabilitiesJsonModels.CapabilitiesMinimalPayload minimalPayload(
      CapabilitiesDescriptor capabilitiesDescriptor) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    return new CliDiscoveryCapabilitiesJsonModels.CapabilitiesMinimalPayload(
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
        "See --detail compact.",
        "See --detail full.");
  }

  static CliDiscoveryCapabilitiesJsonModels.CapabilitiesCompactPayload compactPayload(
      CapabilitiesDescriptor capabilitiesDescriptor) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    return new CliDiscoveryCapabilitiesJsonModels.CapabilitiesCompactPayload(
        capabilitiesDescriptor.application(),
        capabilitiesDescriptor.version(),
        DiscoveryDetail.COMPACT,
        DiscoveryFocus.OVERVIEW,
        capabilitiesDescriptor.storage().bookBoundary(),
        capabilitiesDescriptor.storage().engines().stream().map(Object::toString).toList(),
        requestInputCompactPayload(capabilitiesDescriptor),
        CliDiscoveryCapabilitiesPayloadMapper.commandSurfacePayloads(
            capabilitiesDescriptor.commands().allCommands(),
            capabilitiesDescriptor.requestInput().requestFileCommands()),
        "Use '"
            + CliInvocationText.commandExample(OperationId.CAPABILITIES)
            + " --output json "
            + ProtocolOptions.FOCUS
            + " commands' for category-filtered command families, or rerun with '"
            + CliInvocationText.commandExample(OperationId.CAPABILITIES)
            + " --output json "
            + ProtocolOptions.DETAIL
            + " full' for exhaustive schema, response, and doctrine details.");
  }

  static CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload fullPayload(
      CapabilitiesDescriptor capabilitiesDescriptor) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    return new CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload(
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

  static CliDiscoveryCommonJsonModels.RequestInputCompactPayload requestInputCompactPayload(
      CapabilitiesDescriptor capabilitiesDescriptor) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    return new CliDiscoveryCommonJsonModels.RequestInputCompactPayload(
        capabilitiesDescriptor.requestInput().bookFileOption(),
        capabilitiesDescriptor.requestInput().bookPassphraseOptions(),
        capabilitiesDescriptor.requestInput().requestFileOption(),
        capabilitiesDescriptor.requestInput().requestFileCommands(),
        capabilitiesDescriptor.requestInput().stdinToken(),
        capabilitiesDescriptor.requestInput().outputOption());
  }
}
