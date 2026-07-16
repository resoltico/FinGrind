package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.protocol.CapabilityCatalogEntry;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.runtime.ContractResponse;
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

  record CapabilitiesCurrencySlicePayload(ContractResponse.CurrencyDescriptor currencyModel)
      implements ProtocolSuccessPayload {
    public CapabilitiesCurrencySlicePayload {
      currencyModel = requireValue(currencyModel, "currencyModel");
    }
  }

  record CapabilitiesKernelSlicePayload(
      ContractResponse.BookkeepingKernelDescriptor bookkeepingKernel)
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

  record CapabilitiesResponseContractSlicePayload(
      ContractResponse.ResponseModelDescriptor responseModel,
      ContractResponse.PlanExecutionDescriptor planExecution,
      ContractResponse.AuditDescriptor audit,
      ContractResponse.AccountRegistryDescriptor accountRegistry,
      ContractResponse.ReversalDescriptor reversals,
      ContractResponse.PreflightDescriptor preflight)
      implements ProtocolSuccessPayload {
    public CapabilitiesResponseContractSlicePayload {
      responseModel = requireValue(responseModel, "responseModel");
      planExecution = requireValue(planExecution, "planExecution");
      audit = requireValue(audit, "audit");
      accountRegistry = requireValue(accountRegistry, "accountRegistry");
      reversals = requireValue(reversals, "reversals");
      preflight = requireValue(preflight, "preflight");
    }
  }

  record CapabilitiesResponseContractCompactPayload(
      ContractResponse.ResponseModelDescriptor responseModel,
      String preflightSemantics,
      String planJournal,
      String reversalModel,
      int requestProvenanceFieldCount,
      int committedFieldCount)
      implements ProtocolSuccessPayload {
    public CapabilitiesResponseContractCompactPayload {
      responseModel = requireValue(responseModel, "responseModel");
      preflightSemantics = requireText(preflightSemantics, "preflightSemantics");
      planJournal = requireText(planJournal, "planJournal");
      reversalModel = requireText(reversalModel, "reversalModel");
      if (requestProvenanceFieldCount < 0) {
        throw new IllegalArgumentException(
            "requestProvenanceFieldCount must be greater than or equal to zero.");
      }
      if (committedFieldCount < 0) {
        throw new IllegalArgumentException(
            "committedFieldCount must be greater than or equal to zero.");
      }
    }
  }

  record CapabilitiesResponseContractSummaryPayload(
      List<String> envelopeStatusCodes,
      String preflightSemantics,
      String planJournal,
      String reversalModel,
      int requestProvenanceFieldCount,
      int committedFieldCount)
      implements ProtocolSuccessPayload {
    public CapabilitiesResponseContractSummaryPayload {
      envelopeStatusCodes = copyList(envelopeStatusCodes, "envelopeStatusCodes");
      preflightSemantics = requireText(preflightSemantics, "preflightSemantics");
      planJournal = requireText(planJournal, "planJournal");
      reversalModel = requireText(reversalModel, "reversalModel");
      if (requestProvenanceFieldCount < 0) {
        throw new IllegalArgumentException(
            "requestProvenanceFieldCount must be greater than or equal to zero.");
      }
      if (committedFieldCount < 0) {
        throw new IllegalArgumentException(
            "committedFieldCount must be greater than or equal to zero.");
      }
    }
  }
}
