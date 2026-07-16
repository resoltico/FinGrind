package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.CapabilityCatalogEntry;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import java.util.List;

/** Descriptor for the capabilities payload. */
public record CapabilitiesDescriptor(
    String application,
    String version,
    String protocolVersion,
    StorageSurfaceDescriptor storage,
    CommandCatalogDescriptor commands,
    ContractRequestShapes.RequestInputDescriptor requestInput,
    ContractRequestShapes.RequestShapesDescriptor requestShapes,
    ContractResponse.ResponseModelDescriptor responseModel,
    ContractResponse.PlanExecutionDescriptor planExecution,
    ContractResponse.AuditDescriptor audit,
    ContractResponse.AccountRegistryDescriptor accountRegistry,
    ContractResponse.ReversalDescriptor reversals,
    ContractResponse.PreflightDescriptor preflight,
    ContractResponse.CurrencyDescriptor currencyModel,
    ContractResponse.BookkeepingKernelDescriptor bookkeepingKernel,
    List<CapabilityCatalogEntry> capabilityCatalog)
    implements ContractDiscoveryDescriptor {
  /** Validates one capabilities descriptor payload. */
  public CapabilitiesDescriptor {
    application = ContractDescriptorValidation.requireText(application, "application");
    version = ContractDescriptorValidation.requireText(version, "version");
    protocolVersion = ContractDescriptorValidation.requireText(protocolVersion, "protocolVersion");
    storage = ContractDescriptorValidation.requireValue(storage, "storage");
    commands = ContractDescriptorValidation.requireValue(commands, "commands");
    requestInput = ContractDescriptorValidation.requireValue(requestInput, "requestInput");
    requestShapes = ContractDescriptorValidation.requireValue(requestShapes, "requestShapes");
    responseModel = ContractDescriptorValidation.requireValue(responseModel, "responseModel");
    planExecution = ContractDescriptorValidation.requireValue(planExecution, "planExecution");
    audit = ContractDescriptorValidation.requireValue(audit, "audit");
    accountRegistry = ContractDescriptorValidation.requireValue(accountRegistry, "accountRegistry");
    reversals = ContractDescriptorValidation.requireValue(reversals, "reversals");
    preflight = ContractDescriptorValidation.requireValue(preflight, "preflight");
    currencyModel = ContractDescriptorValidation.requireValue(currencyModel, "currencyModel");
    bookkeepingKernel =
        ContractDescriptorValidation.requireValue(bookkeepingKernel, "bookkeepingKernel");
    capabilityCatalog =
        ContractDescriptorValidation.copyList(capabilityCatalog, "capabilityCatalog");
  }
}
