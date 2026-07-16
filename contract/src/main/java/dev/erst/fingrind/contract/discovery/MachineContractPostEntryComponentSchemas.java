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
        "Balanced journal line.", MachineContractPostEntryNestedFieldSpecs.lineFields());
  }

  static Map<String, Object> openingBalanceSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Opening balance inside the initial accounting position.",
        MachineContractPostEntryNestedFieldSpecs.openingBalanceFields());
  }

  static Map<String, Object> taxSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional declared tax selector resolved through an owned tax registration.",
        MachineContractPostEntryNestedFieldSpecs.taxFields());
  }

  static Map<String, Object> settlementAdjunctSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional settlement-side adjunct carried by receipt and payment requests.",
        MachineContractPostEntryNestedFieldSpecs.settlementAdjunctFields());
  }

  static Map<String, Object> inventoryReliefSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional trading-sale inventory-relief facts that debit cost of sales and credit inventory as part of the same sale entry.",
        MachineContractPostEntryNestedFieldSpecs.inventoryReliefFields());
  }

  static Map<String, Object> recognitionIntervalSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Inclusive date interval in which a deferred balance may be recognized.",
        MachineContractPostEntryNestedFieldSpecs.recognitionIntervalFields());
  }

  static Map<String, Object> fixedAssetDepreciationScheduleSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Straight-line depreciation terms retained with one capitalized fixed asset.",
        MachineContractPostEntryNestedFieldSpecs.fixedAssetDepreciationScheduleFields());
  }

  static Map<String, Object> foreignExchangeSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional owned foreign-exchange facts for a transaction-currency event translated into book functional currency.",
        MachineContractPostEntryForeignExchangeFieldSpecs.foreignExchangeFields());
  }

  static Map<String, Object> quotedRateSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Owned exact quoted exchange-rate facts linking a transaction-currency amount to a functional amount.",
        MachineContractPostEntryForeignExchangeFieldSpecs.quotedRateFields());
  }

  static Map<String, Object> provenanceSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Caller-supplied provenance captured before commit.",
        MachineContractPostEntryNestedFieldSpecs.provenanceFields());
  }

  static Map<String, Object> evidenceSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "First-class source-document and approval references linked to this posting.",
        MachineContractPostEntryEvidenceFieldSpecs.evidenceFields());
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
        "Retained source document linked to this posting.",
        MachineContractPostEntryEvidenceFieldSpecs.sourceDocumentFields());
  }

  static Map<String, Object> sourceDocumentSchema(
      RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts) {
    return MachineContractSchemaSupport.objectSchema(
        "Retained source document linked to this posting.",
        MachineContractPostEntryEvidenceFieldSpecs.sourceDocumentFields(entryKindFacts));
  }

  static Map<String, Object> approvalSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Retained approval linked to this posting.",
        MachineContractPostEntryEvidenceFieldSpecs.approvalFields());
  }

  static Map<String, Object> reversalSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional reversal target descriptor.",
        MachineContractPostEntryNestedFieldSpecs.reversalFields());
  }

  static MachineContractFieldSpec requiredEntryKindField(
      BookkeepingEntryKind kind, String description) {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
        description,
        MachineContractScalarSchemas.constSchema(kind.wireValue(), description));
  }
}
