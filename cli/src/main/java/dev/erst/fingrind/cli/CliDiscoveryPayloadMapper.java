package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;

/** Maps discovery descriptors onto narrower CLI JSON payloads. */
final class CliDiscoveryPayloadMapper {
  private CliDiscoveryPayloadMapper() {}

  static ProtocolSuccessPayload helpPayload(HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    return CliDiscoveryHelpPayloadMapper.helpPayload(helpDescriptor, detail);
  }

  static ProtocolSuccessPayload capabilitiesPayloadAny(
      CapabilitiesDescriptor capabilitiesDescriptor, DiscoveryDetail detail) {
    return CliDiscoveryCapabilitiesPayloadMapper.capabilitiesPayload(
        capabilitiesDescriptor, detail);
  }

  static ProtocolSuccessPayload capabilitiesPayload(
      CapabilitiesDescriptor capabilitiesDescriptor, DiscoveryDetail detail) {
    return capabilitiesPayloadAny(capabilitiesDescriptor, detail);
  }
}
