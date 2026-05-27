package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentType;
import java.util.List;
import java.util.Map;

/** Field specifications for machine-readable post-entry requests. */
final class MachineContractPostEntryFieldSpecs {
  private MachineContractPostEntryFieldSpecs() {}

  static List<MachineContractFieldSpec> topLevelFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
            "Caller-authored bookkeeping entry kind. Typed business events remain primary, and administrative adjustments use named entry kinds rather than a generic raw-journal tunnel.",
            MachineContractScalarSchemas.enumStringSchema(
                "Caller-authored bookkeeping entry kind.", BookkeepingEntryKind.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
            "ISO-8601 local date that makes the bookkeeping entry effective.",
            MachineContractScalarSchemas.dateStringSchema(
                "ISO-8601 local date that makes the bookkeeping entry effective.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account used by one cash-settled business event.",
            accountCodeSchema("Declared cash account used by one cash-settled business event.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
            "Declared revenue account credited by one cash-revenue business event.",
            accountCodeSchema(
                "Declared revenue account credited by one cash-revenue business event.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
            "Declared expense account debited by one cash-expense business event.",
            accountCodeSchema(
                "Declared expense account debited by one cash-expense business event.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            "Declared equity account used by equity contribution or withdrawal business events.",
            accountCodeSchema(
                "Declared equity account used by equity contribution or withdrawal business events.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.AMOUNT,
            "Exact positive money object carried by one typed business event.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive money object carried by one typed business event.", true)),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.LINES,
            "Balanced non-empty array of journal lines used only by named administrative adjustment entries.",
            MachineContractSchemaSupport.arraySchema(
                "Balanced non-empty array of journal lines used only by named administrative adjustment entries.",
                MachineContractPostEntryVariantSchemas.lineSchema(),
                2)),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EVIDENCE,
            "First-class retained source-document and approval evidence linked to this bookkeeping entry.",
            MachineContractPostEntryVariantSchemas.evidenceSchema()),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.PROVENANCE,
            "Caller-supplied request provenance captured before commit.",
            MachineContractPostEntryVariantSchemas.provenanceSchema()),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.REVERSAL,
            "Required reversal target descriptor for REVERSAL_ADJUSTMENT and absent otherwise.",
            MachineContractPostEntryVariantSchemas.reversalSchema()),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.TopLevel.CORRECTION,
            "Legacy correction payloads are hard-broken and no longer accepted."));
  }

  static List<MachineContractFieldSpec> lineFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE,
            "Declared book-local account code for this journal line. FinGrind accepts ASCII letters or digits followed by ASCII letters, digits, '.', '_', ':', '/', or '-'.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Declared book-local account code for this journal line.",
                AccountCode.pattern(),
                AccountCode.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.SIDE,
            "Journal side that carries the line amount.",
            MachineContractScalarSchemas.enumStringSchema(
                "Journal side that carries the line amount.", JournalLine.EntrySide.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.AMOUNT,
            "Exact positive money object for this journal line. Every line in one entry must resolve to the same currency unit.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive money object for this journal line.", true)));
  }

  static List<MachineContractFieldSpec> evidenceFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Evidence.SOURCE_DOCUMENTS,
            "Non-empty ordered source-document references linked to this posting.",
            MachineContractSchemaSupport.arraySchema(
                "Non-empty ordered source-document references linked to this posting.",
                MachineContractPostEntryVariantSchemas.sourceDocumentSchema(),
                1)),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Evidence.APPROVALS,
            "Ordered approval references linked to this posting. The list may be empty when no approval exists for the posting.",
            MachineContractSchemaSupport.arraySchema(
                "Ordered approval references linked to this posting.",
                MachineContractPostEntryVariantSchemas.approvalSchema(),
                0)));
  }

  static List<MachineContractFieldSpec> sourceDocumentFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_ID,
            "Stable identifier of one retained source document.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Stable identifier of one retained source document.",
                SourceDocumentId.pattern(),
                SourceDocumentId.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_TYPE,
            "Caller-authored source-document classification token.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Caller-authored source-document classification token.",
                SourceDocumentType.pattern(),
                SourceDocumentType.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.DOCUMENT_DATE,
            "Economic or issuance date carried by the retained source document.",
            MachineContractScalarSchemas.dateStringSchema(
                "Economic or issuance date carried by the retained source document.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.CAPTURED_AT,
            "UTC timestamp when the retained source document was captured into evidence custody.",
            MachineContractScalarSchemas.instantStringSchema(
                "UTC timestamp when the retained source document was captured into evidence custody.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.STORAGE_LOCATOR,
            "Stable retained locator for the stored evidence artifact.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Stable retained locator for the stored evidence artifact.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.CONTENT_SHA256,
            "Lowercase SHA-256 hex digest of the retained evidence artifact content.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Lowercase SHA-256 hex digest of the retained evidence artifact content.",
                "^[0-9a-f]{64}$",
                64)));
  }

  static List<MachineContractFieldSpec> approvalFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVAL_ID,
            "Stable identifier of one retained approval fact.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Stable identifier of one retained approval fact.",
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
            ProtocolPostEntryFields.Approval.APPROVER_ID,
            "Stable identifier of the approving actor retained with this approval fact.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Stable identifier of the approving actor retained with this approval fact.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVER_TYPE,
            "Approver classification from the live actorType enum vocabulary.",
            MachineContractScalarSchemas.enumStringSchema(
                "Approver classification from the live actorType enum vocabulary.",
                ActorType.wireValues())),
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

  static List<MachineContractFieldSpec> provenanceFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.ACTOR_ID,
            "Stable identifier of the actor that initiated the request.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Stable identifier of the actor that initiated the request.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.ACTOR_TYPE,
            "Actor classification from the live actorType enum vocabulary.",
            MachineContractScalarSchemas.enumStringSchema(
                "Actor classification from the live actorType enum vocabulary.",
                ActorType.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.COMMAND_ID,
            "Caller-generated command identity for this request.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Caller-generated command identity for this request.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.IDEMPOTENCY_KEY,
            "Book-local idempotency key used to detect duplicate commit attempts. FinGrind accepts ASCII letters or digits followed by ASCII letters, digits, '.', '_', ':', '/', or '-'.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Book-local idempotency key used to detect duplicate commit attempts.",
                IdempotencyKey.pattern(),
                IdempotencyKey.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.CAUSATION_ID,
            "Caller-supplied causation identifier for upstream traceability.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Caller-supplied causation identifier for upstream traceability.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.Provenance.CORRELATION_ID,
            "Optional correlation identifier for joining related calls.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Optional correlation identifier for joining related calls.")),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.Provenance.RECORDED_AT,
            "Committed audit timestamps are generated by FinGrind, not caller input."),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.Provenance.SOURCE_CHANNEL,
            "Committed source channel is generated by FinGrind, not caller input."));
  }

  static List<MachineContractFieldSpec> reversalFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Reversal.PRIOR_POSTING_ID,
            "Existing committed posting identifier that this request reverses.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Existing committed posting identifier that this request reverses.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Reversal.REASON,
            "Plain-language operator explanation attached to this reversal.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Plain-language operator explanation attached to this reversal.")),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.Reversal.KIND,
            "Legacy reversal-kind routing is removed; FinGrind is reversal-only."));
  }

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
        "Exact positive money object carried by this typed business event.",
        MachineContractScalarSchemas.moneyObjectSchema(
            "Exact positive money object carried by this typed business event.", true));
  }

  static MachineContractFieldSpec requiredEvidenceField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.EVIDENCE,
        "First-class retained source-document and approval evidence linked to this bookkeeping entry.",
        MachineContractPostEntryVariantSchemas.evidenceSchema());
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
