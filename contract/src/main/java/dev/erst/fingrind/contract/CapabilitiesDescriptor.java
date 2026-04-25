package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Descriptor for the capabilities payload. */
public record CapabilitiesDescriptor(
    String application,
    String version,
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
    EnvironmentDescriptor environment,
    String timestamp)
    implements ContractDiscoveryDescriptor {
  /** Validates one capabilities descriptor payload. */
  public CapabilitiesDescriptor {
    application = ContractDescriptorValidation.requireText(application, "application");
    version = ContractDescriptorValidation.requireText(version, "version");
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
    environment = ContractDescriptorValidation.requireValue(environment, "environment");
    timestamp = ContractDescriptorValidation.requireText(timestamp, "timestamp");
  }
}
