package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestInputDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Overview-oriented capability discovery JSON record families emitted by the CLI transport layer.
 */
public interface CliDiscoveryCapabilitiesJsonModels
    extends CliDiscoveryCommonJsonModels, CliDiscoveryCapabilitiesSliceJsonModels {

  record CapabilitiesMinimalPayload(
      String application,
      String version,
      String protocolVersion,
      DiscoveryDetail detail,
      DiscoveryFocus focus,
      String kernelScope,
      List<String> builtInStatements,
      String bookBoundary,
      String currencyScope,
      String multiCurrencyStatus,
      RequestInputCompactPayload requestInput,
      String compactDetailHint,
      String fullDetailHint)
      implements ProtocolSuccessPayload {
    public CapabilitiesMinimalPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      protocolVersion = requireText(protocolVersion, "protocolVersion");
      detail = requireValue(detail, "detail");
      focus = requireValue(focus, "focus");
      kernelScope = requireText(kernelScope, "kernelScope");
      builtInStatements = copyList(builtInStatements, "builtInStatements");
      bookBoundary = requireText(bookBoundary, "bookBoundary");
      currencyScope = requireText(currencyScope, "currencyScope");
      multiCurrencyStatus = requireText(multiCurrencyStatus, "multiCurrencyStatus");
      requestInput = requireValue(requestInput, "requestInput");
      compactDetailHint = requireText(compactDetailHint, "compactDetailHint");
      fullDetailHint = requireText(fullDetailHint, "fullDetailHint");
      if (detail != DiscoveryDetail.MINIMAL) {
        throw new IllegalArgumentException("minimal capabilities payload requires minimal detail.");
      }
      if (focus != DiscoveryFocus.OVERVIEW) {
        throw new IllegalArgumentException("minimal overview payload requires overview focus.");
      }
    }
  }

  record CapabilitiesPayload(
      String application,
      String version,
      String protocolVersion,
      DiscoveryDetail detail,
      DiscoveryFocus focus,
      StorageSurfaceDescriptor storage,
      CommandCatalogDescriptor commands,
      RequestInputDescriptor requestInput,
      List<String> machineGuidance,
      @Nullable CapabilitiesDescriptor fullContract)
      implements ProtocolSuccessPayload {
    public CapabilitiesPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      protocolVersion = requireText(protocolVersion, "protocolVersion");
      detail = requireValue(detail, "detail");
      focus = requireValue(focus, "focus");
      storage = requireValue(storage, "storage");
      commands = requireValue(commands, "commands");
      requestInput = requireValue(requestInput, "requestInput");
      machineGuidance = copyList(machineGuidance, "machineGuidance");
      if (detail == DiscoveryDetail.FULL && fullContract == null) {
        throw new IllegalArgumentException("fullContract must be present when detail is full.");
      }
      if (detail != DiscoveryDetail.FULL && fullContract != null) {
        throw new IllegalArgumentException("fullContract must be absent unless detail is full.");
      }
      if (focus != DiscoveryFocus.OVERVIEW) {
        throw new IllegalArgumentException("full overview payload requires overview focus.");
      }
    }
  }

  record CapabilitiesCompactPayload(
      String application,
      String version,
      String protocolVersion,
      DiscoveryDetail detail,
      DiscoveryFocus focus,
      String bookBoundary,
      List<String> storageEngines,
      RequestInputCompactPayload requestInput,
      List<CommandSurfacePayload> commands,
      String fullDetailHint)
      implements ProtocolSuccessPayload {
    public CapabilitiesCompactPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      protocolVersion = requireText(protocolVersion, "protocolVersion");
      detail = requireValue(detail, "detail");
      focus = requireValue(focus, "focus");
      bookBoundary = requireText(bookBoundary, "bookBoundary");
      storageEngines = copyList(storageEngines, "storageEngines");
      requestInput = requireValue(requestInput, "requestInput");
      commands = copyList(commands, "commands");
      fullDetailHint = requireText(fullDetailHint, "fullDetailHint");
      if (detail != DiscoveryDetail.COMPACT) {
        throw new IllegalArgumentException("compact capabilities payload requires compact detail.");
      }
      if (focus != DiscoveryFocus.OVERVIEW) {
        throw new IllegalArgumentException("compact overview payload requires overview focus.");
      }
    }
  }
}
