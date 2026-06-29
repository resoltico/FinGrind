package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.Map;

/** Variant schema builders for post-entry request shapes. */
final class MachineContractPostEntryVariantSchemas {
  private MachineContractPostEntryVariantSchemas() {}

  static Map<String, Object> postEntrySchema() {
    return MachineContractPostEntryVariantSchemaBuilders.postEntrySchema();
  }

  static Map<String, Object> schema(BookkeepingEntryKind entryKind) {
    return MachineContractPostEntryVariantSchemaBuilders.schema(entryKind);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor template(
      BookkeepingEntryKind entryKind) {
    return MachineContractPostEntryVariantTemplates.template(entryKind);
  }

  static Map<String, Object> lineSchema() {
    return MachineContractPostEntryComponentSchemas.lineSchema();
  }

  static Map<String, Object> openingBalanceSchema() {
    return MachineContractPostEntryComponentSchemas.openingBalanceSchema();
  }

  static Map<String, Object> provenanceSchema() {
    return MachineContractPostEntryComponentSchemas.provenanceSchema();
  }

  static Map<String, Object> evidenceSchema() {
    return MachineContractPostEntryComponentSchemas.evidenceSchema();
  }

  static Map<String, Object> evidenceSchema(
      RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts) {
    return MachineContractPostEntryComponentSchemas.evidenceSchema(entryKindFacts);
  }

  static Map<String, Object> sourceDocumentSchema() {
    return MachineContractPostEntryComponentSchemas.sourceDocumentSchema();
  }

  static Map<String, Object> sourceDocumentSchema(
      RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts) {
    return MachineContractPostEntryComponentSchemas.sourceDocumentSchema(entryKindFacts);
  }

  static Map<String, Object> approvalSchema() {
    return MachineContractPostEntryComponentSchemas.approvalSchema();
  }

  static Map<String, Object> reversalTargetSchema() {
    return MachineContractPostEntryComponentSchemas.reversalSchema();
  }
}
