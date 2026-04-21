package dev.erst.fingrind.contract;

import java.util.List;

/** Shared descriptor façade for the public machine-readable FinGrind contract. */
final class MachineContractSupport {
  private MachineContractSupport() {}

  static ContractResponse.BookModelDescriptor bookModel() {
    return MachineContractDomainSupport.bookModel();
  }

  static List<ContractDiscovery.CommandDescriptor> commandDescriptors() {
    return MachineContractDomainSupport.commandDescriptors();
  }

  static List<ContractDiscovery.ExitCodeDescriptor> exitCodes() {
    return MachineContractDomainSupport.exitCodes();
  }

  static ContractRequestShapes.RequestInputDescriptor requestInput() {
    return MachineContractRequestInputSupport.requestInput();
  }

  static ContractRequestShapes.RequestShapesDescriptor requestShapes() {
    return MachineContractRequestShapeSupport.requestShapes();
  }

  static ContractResponse.ResponseModelDescriptor responseModel() {
    return MachineContractResponseSupport.responseModel();
  }

  static ContractResponse.AuditDescriptor audit() {
    return MachineContractDomainSupport.audit();
  }

  static ContractResponse.AccountRegistryDescriptor accountRegistry() {
    return MachineContractDomainSupport.accountRegistry();
  }

  static ContractResponse.ReversalDescriptor reversals() {
    return MachineContractDomainSupport.reversals();
  }

  static ContractResponse.PreflightDescriptor preflight() {
    return MachineContractDomainSupport.preflight();
  }

  static ContractResponse.CurrencyDescriptor currencyModel() {
    return MachineContractDomainSupport.currencyModel();
  }

  static ContractResponse.PlanExecutionDescriptor planExecution() {
    return MachineContractDomainSupport.planExecution();
  }
}
