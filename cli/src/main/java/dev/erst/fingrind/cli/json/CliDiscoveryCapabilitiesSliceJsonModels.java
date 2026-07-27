package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.protocol.CapabilityCatalogEntry;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.runtime.BookkeepingKernelDescriptor;
import dev.erst.fingrind.contract.runtime.CurrencyDescriptor;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Focused capability-slice JSON record families emitted by the CLI transport layer. */
public interface CliDiscoveryCapabilitiesSliceJsonModels extends CliDiscoveryCommonJsonModels {

  record CapabilitiesSlicePayload(
      String application,
      String version,
      String protocolVersion,
      DiscoveryDetail detail,
      DiscoveryFocus focus,
      @Nullable String category,
      ProtocolSuccessPayload data,
      List<String> nextHints)
      implements ProtocolSuccessPayload {
    public CapabilitiesSlicePayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      protocolVersion = requireText(protocolVersion, "protocolVersion");
      detail = requireValue(detail, "detail");
      focus = requireValue(focus, "focus");
      category = requireOptionalText(category, "category");
      data = requireValue(data, "data");
      nextHints = copyList(nextHints, "nextHints");
      if (focus == DiscoveryFocus.OVERVIEW) {
        throw new IllegalArgumentException("slice payload requires a non-overview focus.");
      }
    }
  }

  record CapabilitiesStorageSlicePayload(StorageSurfaceDescriptor storage)
      implements ProtocolSuccessPayload {
    public CapabilitiesStorageSlicePayload {
      storage = requireValue(storage, "storage");
    }
  }

  record CapabilitiesCurrencySlicePayload(CurrencyDescriptor currencyModel)
      implements ProtocolSuccessPayload {
    public CapabilitiesCurrencySlicePayload {
      currencyModel = requireValue(currencyModel, "currencyModel");
    }
  }

  record CapabilitiesKernelSlicePayload(BookkeepingKernelDescriptor bookkeepingKernel)
      implements ProtocolSuccessPayload {
    public CapabilitiesKernelSlicePayload {
      bookkeepingKernel = requireValue(bookkeepingKernel, "bookkeepingKernel");
    }
  }

  record CapabilitiesCatalogSlicePayload(List<CapabilityCatalogEntry> capabilityCatalog)
      implements ProtocolSuccessPayload {
    public CapabilitiesCatalogSlicePayload {
      capabilityCatalog = copyList(capabilityCatalog, "capabilityCatalog");
    }
  }
}
