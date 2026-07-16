package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestInputDescriptor;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import org.jspecify.annotations.Nullable;

/** Request-input-focused JSON record family emitted by the CLI discovery transport. */
public interface CliDiscoveryRequestInputSliceJsonModels {

  record CapabilitiesRequestInputSlicePayload(
      CliDiscoveryCommonJsonModels.RequestInputCompactPayload requestInput,
      @Nullable RequestInputDescriptor fullRequestInput)
      implements ProtocolSuccessPayload {
    public CapabilitiesRequestInputSlicePayload {
      requestInput = requireValue(requestInput, "requestInput");
    }
  }
}
