package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.Map;

/** Shared component schemas used by post-entry request variants. */
final class MachineContractPostEntryComponentSchemas {
  private MachineContractPostEntryComponentSchemas() {}

  static Map<String, Object> lineSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "One balanced journal line.", MachineContractPostEntryFieldSpecs.lineFields());
  }

  static Map<String, Object> openingBalanceSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "One opening balance inside the initial accounting position.",
        MachineContractPostEntryFieldSpecs.openingBalanceFields());
  }

  static Map<String, Object> provenanceSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Caller-supplied provenance captured before commit.",
        MachineContractPostEntryFieldSpecs.provenanceFields());
  }

  static Map<String, Object> evidenceSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "First-class source-document and approval references linked to this posting.",
        MachineContractPostEntryFieldSpecs.evidenceFields());
  }

  static Map<String, Object> sourceDocumentSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "One retained source document linked to this posting.",
        MachineContractPostEntryFieldSpecs.sourceDocumentFields());
  }

  static Map<String, Object> approvalSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "One retained approval linked to this posting.",
        MachineContractPostEntryFieldSpecs.approvalFields());
  }

  static Map<String, Object> reversalSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional reversal target descriptor.",
        MachineContractPostEntryFieldSpecs.reversalFields());
  }

  static MachineContractFieldSpec requiredEntryKindField(
      BookkeepingEntryKind kind, String description) {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
        description,
        MachineContractScalarSchemas.constSchema(kind.wireValue(), description));
  }
}
