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

/** Builds executable JSON Schema documents for posting request shapes. */
final class MachineContractPostEntrySchemas {
  private MachineContractPostEntrySchemas() {}

  static Map<String, Object> postEntrySchema() {
    return MachineContractSchemaSupport.rootOneOfSchema(
        "Canonical bookkeeping entry request JSON document.",
        List.of(
            cashRevenueSchema(),
            cashExpenseSchema(),
            ownerContributionSchema(),
            ownerDrawSchema(),
            openingBalanceAdjustmentSchema(),
            correctionAdjustmentSchema(),
            reversalAdjustmentSchema()));
  }

  static Map<String, Object> postEntrySchemaWithoutDialect() {
    return MachineContractSchemaSupport.stripDialect(postEntrySchema());
  }

  static ContractRequestShapes.PostEntryRequestShapeDescriptor descriptor() {
    return new ContractRequestShapes.PostEntryRequestShapeDescriptor(
        MachineContractSchemaSupport.requestFieldDescriptors(topLevelFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(lineFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(evidenceFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(sourceDocumentFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(approvalFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(provenanceFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(reversalFields()),
        List.of(
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "entryKind", BookkeepingEntryKind.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "lineSide", JournalLine.EntrySide.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor("actorType", ActorType.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "approvalDecision", ApprovalDecision.wireValues())),
        postEntrySchema());
  }

  private static Map<String, Object> lineSchema() {
    return MachineContractSchemaSupport.objectSchema("One balanced journal line.", lineFields());
  }

  private static Map<String, Object> provenanceSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Caller-supplied provenance captured before commit.", provenanceFields());
  }

  private static Map<String, Object> evidenceSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "First-class source-document and approval references linked to this posting.",
        evidenceFields());
  }

  private static Map<String, Object> sourceDocumentSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "One retained source document linked to this posting.", sourceDocumentFields());
  }

  private static Map<String, Object> approvalSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "One retained approval linked to this posting.", approvalFields());
  }

  private static Map<String, Object> reversalSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional reversal target descriptor.", reversalFields());
  }

  private static List<MachineContractFieldSpec> topLevelFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
            "Caller-authored bookkeeping entry kind. Typed business events remain primary, and administrative adjustments use named entry kinds rather than a generic raw-journal tunnel.",
            MachineContractSchemaSupport.enumStringSchema(
                "Caller-authored bookkeeping entry kind.", BookkeepingEntryKind.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
            "ISO-8601 local date that makes the bookkeeping entry effective.",
            MachineContractSchemaSupport.dateStringSchema(
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
            "Declared equity account used by owner contribution or draw business events.",
            accountCodeSchema(
                "Declared equity account used by owner contribution or draw business events.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.AMOUNT,
            "Exact positive money object carried by one typed business event.",
            MachineContractSchemaSupport.moneyObjectSchema(
                "Exact positive money object carried by one typed business event.", true)),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.LINES,
            "Balanced non-empty array of journal lines used only by named administrative adjustment entries.",
            MachineContractSchemaSupport.arraySchema(
                "Balanced non-empty array of journal lines used only by named administrative adjustment entries.",
                lineSchema(),
                2)),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EVIDENCE,
            "First-class retained source-document and approval evidence linked to this bookkeeping entry.",
            evidenceSchema()),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.PROVENANCE,
            "Caller-supplied request provenance captured before commit.",
            provenanceSchema()),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.REVERSAL,
            "Required reversal target descriptor for REVERSAL_ADJUSTMENT and absent otherwise.",
            reversalSchema()),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.TopLevel.CORRECTION,
            "Legacy correction payloads are hard-broken and no longer accepted."));
  }

  private static Map<String, Object> cashRevenueSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Typed bookkeeping event for cash-settled revenue recognized immediately into one revenue account.",
        List.of(
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                "This request records one cash-revenue business event.",
                MachineContractSchemaSupport.constSchema(
                    BookkeepingEntryKind.CASH_REVENUE.wireValue(),
                    "This request records one cash-revenue business event.")),
            requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                "Declared cash account debited by this business event.",
                accountCodeSchema("Declared cash account debited by this business event.")),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
                "Declared revenue account credited by this business event.",
                accountCodeSchema("Declared revenue account credited by this business event.")),
            requiredAmountField(),
            requiredEvidenceField(),
            requiredProvenanceField()));
  }

  private static Map<String, Object> cashExpenseSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Typed bookkeeping event for one cash-settled expense recognized immediately into one expense account.",
        List.of(
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                "This request records one cash-expense business event.",
                MachineContractSchemaSupport.constSchema(
                    BookkeepingEntryKind.CASH_EXPENSE.wireValue(),
                    "This request records one cash-expense business event.")),
            requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
                "Declared expense account debited by this business event.",
                accountCodeSchema("Declared expense account debited by this business event.")),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                "Declared cash account credited by this business event.",
                accountCodeSchema("Declared cash account credited by this business event.")),
            requiredAmountField(),
            requiredEvidenceField(),
            requiredProvenanceField()));
  }

  private static Map<String, Object> ownerContributionSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Typed bookkeeping event for owner capital contributed into cash.",
        List.of(
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                "This request records one owner-contribution business event.",
                MachineContractSchemaSupport.constSchema(
                    BookkeepingEntryKind.OWNER_CONTRIBUTION.wireValue(),
                    "This request records one owner-contribution business event.")),
            requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                "Declared cash account debited by this contribution.",
                accountCodeSchema("Declared cash account debited by this contribution.")),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
                "Declared equity account credited by this contribution.",
                accountCodeSchema("Declared equity account credited by this contribution.")),
            requiredAmountField(),
            requiredEvidenceField(),
            requiredProvenanceField()));
  }

  private static Map<String, Object> ownerDrawSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Typed bookkeeping event for one owner draw paid out of cash.",
        List.of(
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                "This request records one owner-draw business event.",
                MachineContractSchemaSupport.constSchema(
                    BookkeepingEntryKind.OWNER_DRAW.wireValue(),
                    "This request records one owner-draw business event.")),
            requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
                "Declared equity account debited by this draw.",
                accountCodeSchema("Declared equity account debited by this draw.")),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                "Declared cash account credited by this draw.",
                accountCodeSchema("Declared cash account credited by this draw.")),
            requiredAmountField(),
            requiredEvidenceField(),
            requiredProvenanceField()));
  }

  private static Map<String, Object> openingBalanceAdjustmentSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Explicit administrative opening-balance entry used to seed one book before operating activity begins.",
        List.of(
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                "This request records one opening-balance administrative entry.",
                MachineContractSchemaSupport.constSchema(
                    BookkeepingEntryKind.OPENING_BALANCE_ADJUSTMENT.wireValue(),
                    "This request records one opening-balance administrative entry.")),
            requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.LINES,
                "Balanced non-empty array of journal lines for one opening-balance administrative entry.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of journal lines for one opening-balance administrative entry.",
                    lineSchema(),
                    2)),
            requiredEvidenceField(),
            requiredProvenanceField()));
  }

  private static Map<String, Object> correctionAdjustmentSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Explicit administrative correction entry for non-opening, non-reversal adjustments outside the typed business-event family.",
        List.of(
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                "This request records one correction administrative entry.",
                MachineContractSchemaSupport.constSchema(
                    BookkeepingEntryKind.CORRECTION_ADJUSTMENT.wireValue(),
                    "This request records one correction administrative entry.")),
            requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.LINES,
                "Balanced non-empty array of journal lines for one correction administrative entry.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of journal lines for one correction administrative entry.",
                    lineSchema(),
                    2)),
            requiredEvidenceField(),
            requiredProvenanceField()));
  }

  private static Map<String, Object> reversalAdjustmentSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Explicit administrative reversal entry that fully negates one previously committed posting.",
        List.of(
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                "This request records one reversal administrative entry.",
                MachineContractSchemaSupport.constSchema(
                    BookkeepingEntryKind.REVERSAL_ADJUSTMENT.wireValue(),
                    "This request records one reversal administrative entry.")),
            requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.LINES,
                "Balanced non-empty array of journal lines for one reversal administrative entry.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of journal lines for one reversal administrative entry.",
                    lineSchema(),
                    2)),
            requiredEvidenceField(),
            requiredProvenanceField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.REVERSAL,
                "Required reversal target descriptor for one reversal administrative entry.",
                reversalSchema())));
  }

  private static MachineContractFieldSpec requiredEffectiveDateField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
        "ISO-8601 local date that makes the bookkeeping entry effective.",
        MachineContractSchemaSupport.dateStringSchema(
            "ISO-8601 local date that makes the bookkeeping entry effective."));
  }

  private static MachineContractFieldSpec requiredAmountField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.AMOUNT,
        "Exact positive money object carried by this typed business event.",
        MachineContractSchemaSupport.moneyObjectSchema(
            "Exact positive money object carried by this typed business event.", true));
  }

  private static MachineContractFieldSpec requiredEvidenceField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.EVIDENCE,
        "First-class retained source-document and approval evidence linked to this bookkeeping entry.",
        evidenceSchema());
  }

  private static MachineContractFieldSpec requiredProvenanceField() {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.PROVENANCE,
        "Caller-supplied request provenance captured before commit.",
        provenanceSchema());
  }

  private static Map<String, Object> accountCodeSchema(String description) {
    return MachineContractSchemaSupport.tokenStringSchema(
        description, AccountCode.pattern(), AccountCode.maxLength());
  }

  private static List<MachineContractFieldSpec> lineFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE,
            "Declared book-local account code for this journal line. FinGrind accepts ASCII letters or digits followed by ASCII letters, digits, '.', '_', ':', '/', or '-'.",
            MachineContractSchemaSupport.tokenStringSchema(
                "Declared book-local account code for this journal line.",
                AccountCode.pattern(),
                AccountCode.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.SIDE,
            "Journal side that carries the line amount.",
            MachineContractSchemaSupport.enumStringSchema(
                "Journal side that carries the line amount.", JournalLine.EntrySide.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.AMOUNT,
            "Exact positive money object for this journal line. Every line in one entry must resolve to the same currency unit.",
            MachineContractSchemaSupport.moneyObjectSchema(
                "Exact positive money object for this journal line.", true)));
  }

  private static List<MachineContractFieldSpec> evidenceFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Evidence.SOURCE_DOCUMENTS,
            "Non-empty ordered source-document references linked to this posting.",
            MachineContractSchemaSupport.arraySchema(
                "Non-empty ordered source-document references linked to this posting.",
                sourceDocumentSchema(),
                1)),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Evidence.APPROVALS,
            "Ordered approval references linked to this posting. The list may be empty when no approval exists for the posting.",
            MachineContractSchemaSupport.arraySchema(
                "Ordered approval references linked to this posting.", approvalSchema(), 0)));
  }

  private static List<MachineContractFieldSpec> sourceDocumentFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_ID,
            "Stable identifier of one retained source document.",
            MachineContractSchemaSupport.tokenStringSchema(
                "Stable identifier of one retained source document.",
                SourceDocumentId.pattern(),
                SourceDocumentId.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_TYPE,
            "Caller-authored source-document classification token.",
            MachineContractSchemaSupport.tokenStringSchema(
                "Caller-authored source-document classification token.",
                SourceDocumentType.pattern(),
                SourceDocumentType.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.DOCUMENT_DATE,
            "Economic or issuance date carried by the retained source document.",
            MachineContractSchemaSupport.dateStringSchema(
                "Economic or issuance date carried by the retained source document.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.CAPTURED_AT,
            "UTC timestamp when the retained source document was captured into evidence custody.",
            MachineContractSchemaSupport.instantStringSchema(
                "UTC timestamp when the retained source document was captured into evidence custody.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.STORAGE_LOCATOR,
            "Stable retained locator for the stored evidence artifact.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Stable retained locator for the stored evidence artifact.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SourceDocument.CONTENT_SHA256,
            "Lowercase SHA-256 hex digest of the retained evidence artifact content.",
            MachineContractSchemaSupport.tokenStringSchema(
                "Lowercase SHA-256 hex digest of the retained evidence artifact content.",
                "^[0-9a-f]{64}$",
                64)));
  }

  private static List<MachineContractFieldSpec> approvalFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVAL_ID,
            "Stable identifier of one retained approval fact.",
            MachineContractSchemaSupport.tokenStringSchema(
                "Stable identifier of one retained approval fact.",
                ApprovalId.pattern(),
                ApprovalId.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVAL_TYPE,
            "Caller-authored approval classification token.",
            MachineContractSchemaSupport.tokenStringSchema(
                "Caller-authored approval classification token.",
                ApprovalType.pattern(),
                ApprovalType.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVER_ID,
            "Stable identifier of the approving actor retained with this approval fact.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Stable identifier of the approving actor retained with this approval fact.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVER_TYPE,
            "Approver classification from the live actorType enum vocabulary.",
            MachineContractSchemaSupport.enumStringSchema(
                "Approver classification from the live actorType enum vocabulary.",
                ActorType.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.DECISION,
            "Retained approval decision for this approval fact.",
            MachineContractSchemaSupport.enumStringSchema(
                "Retained approval decision for this approval fact.",
                ApprovalDecision.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Approval.APPROVED_AT,
            "UTC timestamp when the approval decision was recorded.",
            MachineContractSchemaSupport.instantStringSchema(
                "UTC timestamp when the approval decision was recorded.")));
  }

  private static List<MachineContractFieldSpec> provenanceFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.ACTOR_ID,
            "Stable identifier of the actor that initiated the request.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Stable identifier of the actor that initiated the request.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.ACTOR_TYPE,
            "Actor classification from the live actorType enum vocabulary.",
            MachineContractSchemaSupport.enumStringSchema(
                "Actor classification from the live actorType enum vocabulary.",
                ActorType.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.COMMAND_ID,
            "Caller-generated command identity for this request.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Caller-generated command identity for this request.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.IDEMPOTENCY_KEY,
            "Book-local idempotency key used to detect duplicate commit attempts. FinGrind accepts ASCII letters or digits followed by ASCII letters, digits, '.', '_', ':', '/', or '-'.",
            MachineContractSchemaSupport.tokenStringSchema(
                "Book-local idempotency key used to detect duplicate commit attempts.",
                IdempotencyKey.pattern(),
                IdempotencyKey.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Provenance.CAUSATION_ID,
            "Caller-supplied causation identifier for upstream traceability.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Caller-supplied causation identifier for upstream traceability.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.Provenance.CORRELATION_ID,
            "Optional correlation identifier for joining related calls.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Optional correlation identifier for joining related calls.")),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.Provenance.RECORDED_AT,
            "Committed audit timestamps are generated by FinGrind, not caller input."),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.Provenance.SOURCE_CHANNEL,
            "Committed source channel is generated by FinGrind, not caller input."));
  }

  private static List<MachineContractFieldSpec> reversalFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Reversal.PRIOR_POSTING_ID,
            "Existing committed posting identifier that this request reverses.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Existing committed posting identifier that this request reverses.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Reversal.REASON,
            "Human-readable operator explanation attached to this reversal.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Human-readable operator explanation attached to this reversal.")),
        MachineContractFieldSpec.forbidden(
            ProtocolPostEntryFields.Reversal.KIND,
            "Legacy reversal-kind routing is removed; FinGrind is reversal-only."));
  }
}
