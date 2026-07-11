package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountCode;
import java.util.Map;

/** Shared required-field builders for post-entry machine contracts. */
final class MachineContractPostEntryRequiredFieldSpecs {
  private MachineContractPostEntryRequiredFieldSpecs() {}

  static MachineContractFieldSpec requiredEffectiveDateField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
        "ISO-8601 local date that makes the bookkeeping entry effective.",
        MachineContractScalarSchemas.dateStringSchema(
            "ISO-8601 local date that makes the bookkeeping entry effective."));
  }

  static MachineContractFieldSpec requiredAmountField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.AMOUNT,
        "Exact positive money object carried by this typed business entry.",
        MachineContractScalarSchemas.moneyObjectSchema(
            "Exact positive money object carried by this typed business entry.", true));
  }

  static MachineContractFieldSpec requiredQuantityField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.QUANTITY,
        "Exact positive inventory quantity text carried by this quantity-changing inventory entry.",
        MachineContractScalarSchemas.quantityTextSchema(
            "Exact positive inventory quantity text carried by this quantity-changing inventory entry."));
  }

  static MachineContractFieldSpec requiredUnitCostField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.UNIT_COST,
        "Exact positive functional-currency pre-VAT unit cost carried by this inventory acquisition.",
        MachineContractScalarSchemas.moneyObjectSchema(
            "Exact positive functional-currency pre-VAT unit cost carried by this inventory acquisition.",
            true));
  }

  static MachineContractFieldSpec requiredEvidenceField(
      RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts) {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.EVIDENCE,
        "First-class retained source-document and approval evidence linked to this bookkeeping entry.",
        MachineContractPostEntryVariantSchemas.evidenceSchema(entryKindFacts));
  }

  static MachineContractFieldSpec requiredProvenanceField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.PROVENANCE,
        "Caller-supplied request provenance captured before commit.",
        MachineContractPostEntryVariantSchemas.provenanceSchema());
  }

  static Map<String, Object> accountCodeSchema(String description) {
    return MachineContractScalarSchemas.tokenStringSchema(
        description, AccountCode.pattern(), AccountCode.maxLength());
  }
}
