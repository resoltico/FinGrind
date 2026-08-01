package dev.erst.fingrind.contract.discovery;

/** Builds request-shape descriptors for the machine-readable contract. */
final class MachineContractRequestShapeDescriptors {
  private MachineContractRequestShapeDescriptors() {}

  static ContractRequestShapes.RequestShapesDescriptor requestShapes() {
    return new ContractRequestShapes.RequestShapesDescriptor(
        MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
        MachineContractPostEntrySchemas.descriptor(),
        MachineContractDeclareAccountSchemas.descriptor(),
        MachineContractRetireAccountSchemas.descriptor(),
        MachineContractDeclareTaxRegistrationSchemas.descriptor(),
        MachineContractLedgerPlanSchemas.descriptor());
  }
}
