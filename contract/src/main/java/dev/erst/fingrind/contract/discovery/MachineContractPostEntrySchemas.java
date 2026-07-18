package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolForeignExchangeRequestFields;
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

  static ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor descriptor() {
    RequestSurfaceFacts requestSurface = ProtocolCatalog.domain().requestSurface();
    return new ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor(
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryFieldSpecs.topLevelFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.lineFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.openingBalanceFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.recognitionIntervalFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryForeignExchangeFieldSpecs.foreignExchangeFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryForeignExchangeFieldSpecs.quotedRateFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.taxFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryEvidenceFieldSpecs.evidenceFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryEvidenceFieldSpecs.sourceDocumentFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryEvidenceFieldSpecs.approvalFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.provenanceFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.reversalFields()),
        requestSurface.bookkeepingEntryKinds().stream()
            .map(MachineContractPostEntrySchemas::entryKindSemanticsDescriptor)
            .toList(),
        requestSurface.reachabilityMatrix().stream()
            .map(MachineContractPostEntrySchemas::reachabilityCellDescriptor)
            .toList(),
        new ContractRequestShapes.EvidenceRequirementDescriptor(
            requestSurface.bookkeepingEntryEvidence().description(),
            requestSurface.bookkeepingEntryEvidence().minimumSourceDocuments()),
        List.of(
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolBusinessEventFields.Core.ENTRY_KIND, BookkeepingEntryKind.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.JournalLine.SIDE, JournalLine.EntrySide.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolForeignExchangeRequestFields.ForeignExchange.TREATMENT_KIND,
                ForeignExchangeTreatmentKind.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.Provenance.ACTOR_TYPE, ActorType.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolPostEntryFields.Approval.DECISION, ApprovalDecision.wireValues())),
        postEntrySchema());
  }

  static ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor descriptor(
      BookkeepingEntryKind entryKind) {
    RequestSurfaceFacts requestSurface = ProtocolCatalog.domain().requestSurface();
    RequestSurfaceFacts.BookkeepingEntryKindFacts facts =
        requestSurface.bookkeepingEntryKind(entryKind);
    return new ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor(
        topLevelFieldDescriptors(facts),
        nestedFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.lineFields(),
            facts.requiredTopLevelFields().contains(ProtocolBusinessEventFields.Core.LINES)),
        nestedFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.openingBalanceFields(),
            facts
                .requiredTopLevelFields()
                .contains(ProtocolBusinessEventFields.Core.OPENING_BALANCES)),
        nestedFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.recognitionIntervalFields(),
            facts
                .requiredTopLevelFields()
                .contains(ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL)),
        nestedFieldDescriptors(
            MachineContractPostEntryForeignExchangeFieldSpecs.foreignExchangeFields(),
            facts
                .optionalTopLevelFields()
                .contains(ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE)),
        nestedFieldDescriptors(
            MachineContractPostEntryForeignExchangeFieldSpecs.quotedRateFields(),
            facts
                .optionalTopLevelFields()
                .contains(ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE)),
        nestedFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.taxFields(),
            facts.optionalTopLevelFields().contains(ProtocolBusinessEventFields.Core.TAX)),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryEvidenceFieldSpecs.evidenceFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryEvidenceFieldSpecs.sourceDocumentFields(facts)),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryEvidenceFieldSpecs.approvalFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.provenanceFields()),
        nestedFieldDescriptors(
            MachineContractPostEntryNestedFieldSpecs.reversalFields(),
            facts.requiredTopLevelFields().contains(ProtocolBusinessEventFields.Core.REVERSAL)),
        List.of(entryKindSemanticsDescriptor(facts)),
        requestSurface.reachabilityMatrix().stream()
            .map(MachineContractPostEntrySchemas::reachabilityCellDescriptor)
            .toList(),
        new ContractRequestShapes.EvidenceRequirementDescriptor(
            requestSurface.bookkeepingEntryEvidence().description(),
            requestSurface.bookkeepingEntryEvidence().minimumSourceDocuments()),
        selectedEnumVocabularies(entryKind, facts),
        MachineContractPostEntryVariantSchemas.schema(entryKind));
  }

  private static ContractRequestShapes.EntryKindSemanticsDescriptor entryKindSemanticsDescriptor(
      RequestSurfaceFacts.BookkeepingEntryKindFacts facts) {
    return new ContractRequestShapes.EntryKindSemanticsDescriptor(
        facts.entryKind(),
        facts.requiredTopLevelFields(),
        facts.optionalTopLevelFields(),
        facts.forbiddenTopLevelFields(),
        MachineContractPostEntryVariantSchemas.variantFieldDescriptors(facts.entryKind()),
        facts.requiredSourceDocumentFields(),
        facts.sourceDocumentTypes().mode().wireValue(),
        facts.sourceDocumentTypes().acceptedValues(),
        facts.sourceDocumentTypes().semantics(),
        facts.semantics());
  }

  private static List<ContractRequestShapes.RequestFieldDescriptor> topLevelFieldDescriptors(
      RequestSurfaceFacts.BookkeepingEntryKindFacts facts) {
    return MachineContractPostEntryFieldSpecs.topLevelFields().stream()
        .map(
            field ->
                new ContractRequestShapes.RequestFieldDescriptor(
                    field.name(),
                    facts.requiredTopLevelFields().contains(field.name())
                        ? RequestFieldPresence.REQUIRED
                        : facts.optionalTopLevelFields().contains(field.name())
                            ? RequestFieldPresence.OPTIONAL
                            : conditionallyAcceptedTopLevelField(facts.entryKind(), field)
                                ? RequestFieldPresence.CONDITIONAL
                                : RequestFieldPresence.FORBIDDEN,
                    field.description()))
        .toList();
  }

  private static boolean conditionallyAcceptedTopLevelField(
      BookkeepingEntryKind entryKind, MachineContractFieldSpec field) {
    return field.presence() == RequestFieldPresence.CONDITIONAL
        && ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF.equals(field.name())
        && (entryKind == BookkeepingEntryKind.SALE_SETTLED
            || entryKind == BookkeepingEntryKind.SALE_ON_CREDIT);
  }

  private static List<ContractRequestShapes.RequestFieldDescriptor> nestedFieldDescriptors(
      List<MachineContractFieldSpec> fieldSpecs, boolean accepted) {
    return fieldSpecs.stream()
        .map(
            field ->
                new ContractRequestShapes.RequestFieldDescriptor(
                    field.name(),
                    accepted ? field.presence() : RequestFieldPresence.FORBIDDEN,
                    field.description()))
        .toList();
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

  private static List<ContractRequestShapes.EnumVocabularyDescriptor> selectedEnumVocabularies(
      BookkeepingEntryKind entryKind, RequestSurfaceFacts.BookkeepingEntryKindFacts facts) {
    List<ContractRequestShapes.EnumVocabularyDescriptor> enumVocabularies =
        new java.util.ArrayList<>(
            List.of(
                new ContractRequestShapes.EnumVocabularyDescriptor(
                    ProtocolBusinessEventFields.Core.ENTRY_KIND, List.of(entryKind.wireValue())),
                new ContractRequestShapes.EnumVocabularyDescriptor(
                    ProtocolPostEntryFields.JournalLine.SIDE, JournalLine.EntrySide.wireValues()),
                new ContractRequestShapes.EnumVocabularyDescriptor(
                    ProtocolForeignExchangeRequestFields.ForeignExchange.TREATMENT_KIND,
                    ForeignExchangeTreatmentKind.wireValues()),
                new ContractRequestShapes.EnumVocabularyDescriptor(
                    ProtocolPostEntryFields.Provenance.ACTOR_TYPE, ActorType.wireValues()),
                new ContractRequestShapes.EnumVocabularyDescriptor(
                    ProtocolPostEntryFields.Approval.DECISION, ApprovalDecision.wireValues())));
    if (!facts
        .optionalTopLevelFields()
        .contains(ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE)) {
      enumVocabularies.removeIf(
          vocabulary ->
              ProtocolForeignExchangeRequestFields.ForeignExchange.TREATMENT_KIND.equals(
                  vocabulary.name()));
    }
    if (!facts.sourceDocumentTypes().acceptedValues().isEmpty()) {
      enumVocabularies.add(
          new ContractRequestShapes.EnumVocabularyDescriptor(
              ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_TYPE,
              facts.sourceDocumentTypes().acceptedValues()));
    }
    return List.copyOf(enumVocabularies);
  }
}
