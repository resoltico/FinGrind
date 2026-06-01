package dev.erst.fingrind.contract.discovery;

/** Shared field-spec factories for executable ledger-plan query and assertion shapes. */
final class MachineContractLedgerPlanFieldSpecs {
  private MachineContractLedgerPlanFieldSpecs() {}

  static MachineContractFieldSpec conditionalNonBlankStringField(String name, String description) {
    return MachineContractFieldSpec.conditional(
        name, description, MachineContractScalarSchemas.nonBlankStringSchema(description));
  }

  static MachineContractFieldSpec conditionalDateField(String name, String description) {
    return MachineContractFieldSpec.conditional(
        name, description, MachineContractScalarSchemas.dateStringSchema(description));
  }

  static MachineContractFieldSpec requiredFromConditional(
      MachineContractFieldSpec conditionalField) {
    return MachineContractFieldSpec.required(
        conditionalField.name(),
        conditionalField.description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalField));
  }

  static MachineContractFieldSpec optionalFromConditional(
      MachineContractFieldSpec conditionalField) {
    return MachineContractFieldSpec.optional(
        conditionalField.name(),
        conditionalField.description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalField));
  }
}
