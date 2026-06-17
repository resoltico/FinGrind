package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
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
    RequestSurfaceFacts requestSurface = ProtocolCatalog.domain().requestSurface();
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
        requestSurface.postEntryKinds().stream()
            .map(MachineContractPostEntrySchemas::entryKindSemanticsDescriptor)
            .toList(),
        requestSurface.journalRecipes().stream()
            .map(MachineContractPostEntrySchemas::journalRecipeSemanticsDescriptor)
            .toList(),
        requestSurface.evidenceProfiles().stream()
            .map(MachineContractPostEntrySchemas::evidenceProfileDescriptor)
            .toList(),
        requestSurface.reachabilityMatrix().stream()
            .map(MachineContractPostEntrySchemas::reachabilityCellDescriptor)
            .toList(),
        new ContractRequestShapes.EvidenceRequirementDescriptor(
            requestSurface.postEntryEvidence().description(),
            requestSurface.postEntryEvidence().minimumSourceDocuments(),
            requestSurface.postEntryEvidence().requiredSourceDocumentFields()),
        List.of(
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND, BookkeepingEntryKind.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.TopLevel.RECIPE_KIND, JournalRecipeKind.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.JournalLine.SIDE, JournalLine.EntrySide.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.Provenance.ACTOR_TYPE, ActorType.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.Approval.DECISION, ApprovalDecision.wireValues())),
        postEntrySchema());
  }

  private static ContractRequestShapes.EntryKindSemanticsDescriptor entryKindSemanticsDescriptor(
      RequestSurfaceFacts.PostEntryKindFacts facts) {
    return new ContractRequestShapes.EntryKindSemanticsDescriptor(
        facts.entryKind(),
        facts.requiredTopLevelFields(),
        facts.forbiddenTopLevelFields(),
        facts.evidenceProfileId(),
        facts.semantics());
  }

  private static ContractRequestShapes.JournalRecipeSemanticsDescriptor
      journalRecipeSemanticsDescriptor(RequestSurfaceFacts.JournalRecipeFacts facts) {
    return new ContractRequestShapes.JournalRecipeSemanticsDescriptor(
        facts.recipeKind(),
        facts.requiredTopLevelFields(),
        facts.forbiddenTopLevelFields(),
        facts.evidenceProfileId(),
        facts.semantics());
  }

  private static ContractRequestShapes.EvidenceProfileDescriptor evidenceProfileDescriptor(
      RequestSurfaceFacts.EvidenceProfileFacts facts) {
    return new ContractRequestShapes.EvidenceProfileDescriptor(
        facts.profileId(),
        facts.sourceDocumentTypes().mode().wireValue(),
        facts.sourceDocumentTypes().acceptedValues(),
        facts.sourceDocumentTypes().semantics(),
        facts.semantics());
  }

  private static ContractRequestShapes.ReachabilityCellDescriptor reachabilityCellDescriptor(
      RequestSurfaceFacts.ReachabilityCellFacts facts) {
    return new ContractRequestShapes.ReachabilityCellDescriptor(
        facts.classificationFamily(),
        facts.accountType(),
        facts.classification(),
        facts.declarable(),
        facts.openingReachable(),
        facts.operationalJournalReachable(),
        facts.reversalReachable());
  }
}
