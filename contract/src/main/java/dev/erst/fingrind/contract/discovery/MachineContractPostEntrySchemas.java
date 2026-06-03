package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import java.util.Map;

/** Builds executable JSON Schema documents for posting request shapes. */
final class MachineContractPostEntrySchemas {
  private MachineContractPostEntrySchemas() {}

  static Map<String, Object> postEntrySchema() {
    return MachineContractPostEntryVariantSchemas.postEntrySchema();
  }

  static Map<String, Object> postEntrySchemaWithoutDialect() {
    return MachineContractSchemaSupport.stripDialect(postEntrySchema());
  }

  static ContractRequestShapes.PostEntryRequestShapeDescriptor descriptor() {
    return new ContractRequestShapes.PostEntryRequestShapeDescriptor(
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryFieldSpecs.topLevelFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryFieldSpecs.lineFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryFieldSpecs.openingBalanceFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryFieldSpecs.evidenceFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryFieldSpecs.sourceDocumentFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryFieldSpecs.approvalFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryFieldSpecs.provenanceFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryFieldSpecs.reversalFields()),
        List.of(
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND, BookkeepingEntryKind.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.JournalLine.SIDE, JournalLine.EntrySide.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.Provenance.ACTOR_TYPE, ActorType.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.Approval.DECISION, ApprovalDecision.wireValues())),
        postEntrySchema());
  }
}
