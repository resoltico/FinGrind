package dev.erst.fingrind.contract;

import java.util.List;

/** Shared descriptor façade for the public machine-readable FinGrind contract. */
final class MachineContractDescriptors {
  private MachineContractDescriptors() {}

  static ContractResponse.BookModelDescriptor bookModel() {
    return MachineContractDomainDescriptors.bookModel();
  }

  static List<ContractDiscovery.CommandDescriptor> commandDescriptors() {
    return MachineContractDomainDescriptors.commandDescriptors();
  }

  static List<ContractDiscovery.ExitCodeDescriptor> exitCodes() {
    return MachineContractDomainDescriptors.exitCodes();
  }

  static ContractRequestShapes.RequestInputDescriptor requestInput() {
    return MachineContractRequestInputDescriptors.requestInput();
  }

  static ContractRequestShapes.RequestShapesDescriptor requestShapes() {
    return MachineContractRequestShapeDescriptors.requestShapes();
  }

  static ContractResponse.ResponseModelDescriptor responseModel() {
    return MachineContractResponseDescriptors.responseModel();
  }

  static ContractResponse.AuditDescriptor audit() {
    return MachineContractDomainDescriptors.audit();
  }

  static ContractResponse.AccountRegistryDescriptor accountRegistry() {
    return MachineContractDomainDescriptors.accountRegistry();
  }

  static ContractResponse.ReversalDescriptor reversals() {
    return MachineContractDomainDescriptors.reversals();
  }

  static ContractResponse.PreflightDescriptor preflight() {
    return MachineContractDomainDescriptors.preflight();
  }

  static ContractResponse.CurrencyDescriptor currencyModel() {
    return MachineContractDomainDescriptors.currencyModel();
  }

  static ContractResponse.PlanExecutionDescriptor planExecution() {
    return MachineContractDomainDescriptors.planExecution();
  }
}
