package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
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

  static Map<String, Object> taxSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional declared tax selector resolved through one owned tax registration.",
        MachineContractPostEntryFieldSpecs.taxFields());
  }

  static Map<String, Object> foreignExchangeSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional owned foreign-exchange facts for one transaction-currency event translated into book functional currency.",
        MachineContractPostEntryForeignExchangeFieldSpecs.foreignExchangeFields());
  }

  static Map<String, Object> quotedRateSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Owned exact quoted exchange-rate facts linking one transaction-currency amount to one functional amount.",
        MachineContractPostEntryForeignExchangeFieldSpecs.quotedRateFields());
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

  static Map<String, Object> evidenceSchema(
      RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts) {
    return MachineContractSchemaSupport.objectSchema(
        "First-class source-document and approval references linked to this posting.",
        java.util.List.of(
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.Evidence.SOURCE_DOCUMENTS,
                "Non-empty ordered source-document references linked to this posting. Every posting request must retain at least one source document.",
                MachineContractSchemaSupport.arraySchema(
                    "Non-empty ordered source-document references linked to this posting. Every posting request must retain at least one source document.",
                    sourceDocumentSchema(entryKindFacts),
                    1)),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.Evidence.APPROVALS,
                "Ordered approval references linked to this posting. The list may be empty when no approval exists for the posting.",
                MachineContractSchemaSupport.arraySchema(
                    "Ordered approval references linked to this posting.", approvalSchema(), 0))));
  }

  static Map<String, Object> sourceDocumentSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "One retained source document linked to this posting.",
        MachineContractPostEntryFieldSpecs.sourceDocumentFields());
  }

  static Map<String, Object> sourceDocumentSchema(
      RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts) {
    return MachineContractSchemaSupport.objectSchema(
        "One retained source document linked to this posting.",
        MachineContractPostEntryFieldSpecs.sourceDocumentFields(entryKindFacts));
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
