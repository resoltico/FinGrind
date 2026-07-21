package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.contract.protocol.SourceDocumentTypePolicyMode;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentType;
import java.util.List;
import java.util.Map;

/** Field specifications for bookkeeping-entry evidence, source documents, and approvals. */
final class MachineContractPostEntryEvidenceFieldSpecs {
  private MachineContractPostEntryEvidenceFieldSpecs() {}

  static List<MachineContractFieldSpec> evidenceFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Evidence.SOURCE_DOCUMENTS,
            "Non-empty ordered source-document references linked to this posting. Every posting request must retain at least one source document.",
            MachineContractSchemaSupport.arraySchema(
                "Non-empty ordered source-document references linked to this posting. Every posting request must retain at least one source document.",
                MachineContractPostEntryComponentSchemas.sourceDocumentSchema(),
                1)),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Evidence.APPROVALS,
            "Ordered approval references linked to this posting. The list may be empty when no approval exists for the posting.",
            MachineContractSchemaSupport.arraySchema(
                "Ordered approval references linked to this posting.",
                MachineContractPostEntryComponentSchemas.approvalSchema(),
                0)));
  }

  static List<MachineContractFieldSpec> sourceDocumentFields() {
    return List.of(sourceDocumentIdField(), genericSourceDocumentTypeField(), documentDateField());
  }

  static List<MachineContractFieldSpec> sourceDocumentFields(
      RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts) {
    return List.of(
        sourceDocumentIdField(), sourceDocumentTypeField(entryKindFacts), documentDateField());
  }

  static List<MachineContractFieldSpec> approvalFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVAL_ID,
            "Stable identifier of the retained approval fact.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Stable identifier of the retained approval fact.",
                ApprovalId.pattern(),
                ApprovalId.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVAL_TYPE,
            "Caller-authored approval classification token.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Caller-authored approval classification token.",
                ApprovalType.pattern(),
                ApprovalType.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVER_REFERENCE,
            "Stable external reference retained with this approval fact.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Stable external reference retained with this approval fact.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVER_TYPE,
            "Caller-authored approver classification token.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Caller-authored approver classification token.",
                ApprovalType.pattern(),
                ApprovalType.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.DECISION,
            "Retained approval decision for this approval fact.",
            MachineContractScalarSchemas.enumStringSchema(
                "Retained approval decision for this approval fact.",
                ApprovalDecision.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVED_AT,
            "UTC timestamp when the approval decision was recorded.",
            MachineContractScalarSchemas.instantStringSchema(
                "UTC timestamp when the approval decision was recorded.")));
  }

  private static MachineContractFieldSpec sourceDocumentIdField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_ID,
        "Stable identifier of the retained source document.",
        MachineContractScalarSchemas.tokenStringSchema(
            "Stable identifier of the retained source document.",
            SourceDocumentId.pattern(),
            SourceDocumentId.maxLength()));
  }

  private static MachineContractFieldSpec genericSourceDocumentTypeField() {
    String description =
        "Caller-authored source-document classification token. Inspect bookkeepingEntry.entryKindSemantics[] for the selected entry kind's live policy and any enumerated accepted values.";
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_TYPE,
        description,
        MachineContractScalarSchemas.tokenStringSchema(
            description, SourceDocumentType.pattern(), SourceDocumentType.maxLength()));
  }

  private static MachineContractFieldSpec sourceDocumentTypeField(
      RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts) {
    RequestSurfaceFacts.SourceDocumentTypeFacts sourceDocumentTypes =
        entryKindFacts.sourceDocumentTypes();
    String description =
        sourceDocumentTypes.mode() == SourceDocumentTypePolicyMode.ENUMERATED
            ? "Source-document classification for %s evidence. Accepted values: %s. %s"
                .formatted(
                    entryKindFacts.entryKind().wireValue(),
                    String.join(", ", sourceDocumentTypes.acceptedValues()),
                    sourceDocumentTypes.semantics())
            : "Caller-authored source-document classification token for %s evidence. %s"
                .formatted(entryKindFacts.entryKind().wireValue(), sourceDocumentTypes.semantics());
    Map<String, Object> schema =
        sourceDocumentTypes.mode() == SourceDocumentTypePolicyMode.ENUMERATED
            ? MachineContractScalarSchemas.enumStringSchema(
                description, sourceDocumentTypes.acceptedValues())
            : MachineContractScalarSchemas.tokenStringSchema(
                description, SourceDocumentType.pattern(), SourceDocumentType.maxLength());
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_TYPE, description, schema);
  }

  private static MachineContractFieldSpec documentDateField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.SourceDocument.DOCUMENT_DATE,
        "Economic or issuance date carried by the retained source document.",
        MachineContractScalarSchemas.dateStringSchema(
            "Economic or issuance date carried by the retained source document."));
  }
}
