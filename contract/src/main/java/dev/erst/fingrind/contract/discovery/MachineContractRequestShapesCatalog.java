package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.core.BookkeepingEntryKind;

/** Builds request-shape descriptors for the machine-contract discovery surface. */
final class MachineContractRequestShapesCatalog {
  private MachineContractRequestShapesCatalog() {}

  static ContractRequestShapes.RequestShapesDescriptor postingRequestShapes(
      BookkeepingEntryKind entryKind) {
    return new ContractRequestShapes.RequestShapesDescriptor(
        MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
        MachineContractPostEntrySchemas.descriptor(entryKind),
        null,
        null,
        null);
  }

  static ContractRequestShapes.RequestShapesDescriptor preflightRequestShapes() {
    return new ContractRequestShapes.RequestShapesDescriptor(
        MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
        MachineContractPostEntrySchemas.descriptor(),
        null,
        null,
        null);
  }

  static ContractRequestShapes.RequestShapesDescriptor declareAccountRequestShapes() {
    return new ContractRequestShapes.RequestShapesDescriptor(
        MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
        null,
        MachineContractDeclareAccountSchemas.descriptor(),
        null,
        null);
  }

  static ContractRequestShapes.RequestShapesDescriptor declareTaxRegistrationRequestShapes() {
    return new ContractRequestShapes.RequestShapesDescriptor(
        MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
        null,
        null,
        MachineContractDeclareTaxRegistrationSchemas.descriptor(),
        null);
  }

  static ContractRequestShapes.RequestShapesDescriptor ledgerPlanRequestShapes() {
    return new ContractRequestShapes.RequestShapesDescriptor(
        MachineContractRequestSchemas.JSON_SCHEMA_DIALECT,
        null,
        null,
        null,
        MachineContractLedgerPlanSchemas.descriptor(
            MachineContractTemplatesCatalog.planTemplate().canonicalPostingTemplate().entryKind()));
  }
}
