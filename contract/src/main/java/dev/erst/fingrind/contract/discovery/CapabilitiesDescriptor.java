package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.CapabilityCatalogEntry;
import dev.erst.fingrind.contract.runtime.AccountRegistryDescriptor;
import dev.erst.fingrind.contract.runtime.AuditDescriptor;
import dev.erst.fingrind.contract.runtime.BookkeepingKernelDescriptor;
import dev.erst.fingrind.contract.runtime.CurrencyDescriptor;
import dev.erst.fingrind.contract.runtime.PlanExecutionDescriptor;
import dev.erst.fingrind.contract.runtime.PreflightDescriptor;
import dev.erst.fingrind.contract.runtime.ResponseModelDescriptor;
import dev.erst.fingrind.contract.runtime.ReversalDescriptor;
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
    ResponseModelDescriptor responseModel,
    PlanExecutionDescriptor planExecution,
    AuditDescriptor audit,
    AccountRegistryDescriptor accountRegistry,
    ReversalDescriptor reversals,
    PreflightDescriptor preflight,
    CurrencyDescriptor currencyModel,
    BookkeepingKernelDescriptor bookkeepingKernel,
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
