package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Variant schema builders for post-entry request shapes. */
final class MachineContractPostEntryVariantSchemas {
  private MachineContractPostEntryVariantSchemas() {}

  static Map<String, Object> postEntrySchema() {
    return MachineContractPostEntryVariantSchemaBuilders.postEntrySchema();
  }

  static Map<String, Object> schema(BookkeepingEntryKind entryKind) {
    return MachineContractPostEntryVariantSchemaBuilders.schema(entryKind);
  }

  static List<ContractRequestShapes.RequestFieldDescriptor> variantFieldDescriptors(
      BookkeepingEntryKind entryKind) {
    return MachineContractPostEntryVariantSchemaBuilders.variantFieldDescriptors(entryKind);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor template(
      BookkeepingEntryKind entryKind) {
    return template(entryKind, null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor template(
      BookkeepingEntryKind entryKind, @Nullable BookTemplateId bookTemplateId) {
    return MachineContractPostEntryVariantTemplates.template(entryKind, bookTemplateId);
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
