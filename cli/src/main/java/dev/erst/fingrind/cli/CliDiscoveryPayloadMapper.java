package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import org.jspecify.annotations.Nullable;

/** Maps discovery descriptors onto narrower CLI JSON payloads. */
final class CliDiscoveryPayloadMapper {
  private CliDiscoveryPayloadMapper() {}

  static ProtocolSuccessPayload helpPayload(
      HelpDescriptor helpDescriptor, DiscoveryDetail detail, @Nullable OperationCategory category) {
    return CliDiscoveryHelpPayloadMapper.helpPayload(helpDescriptor, detail, category);
  }

  static ProtocolSuccessPayload capabilitiesPayloadAny(
      CapabilitiesDescriptor capabilitiesDescriptor,
      DiscoveryDetail detail,
      CliDiscoverySelections selections) {
    return CliDiscoveryCapabilitiesPayloadMapper.capabilitiesPayload(
        capabilitiesDescriptor, detail, selections);
  }

  static ProtocolSuccessPayload capabilitiesPayload(
      CapabilitiesDescriptor capabilitiesDescriptor,
      DiscoveryDetail detail,
      CliDiscoverySelections selections) {
    return capabilitiesPayloadAny(capabilitiesDescriptor, detail, selections);
  }
}
