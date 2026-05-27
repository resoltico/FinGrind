package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;

/** Variant schema builders for post-entry request shapes. */
final class MachineContractPostEntryVariantSchemas {
  private MachineContractPostEntryVariantSchemas() {}

  static Map<String, Object> postEntrySchema() {
    return MachineContractSchemaSupport.rootOneOfSchema(
        "Canonical bookkeeping entry request JSON document.",
        List.of(
            cashRevenueSchema(),
            cashExpenseSchema(),
            equityContributionSchema(),
            equityWithdrawalSchema(),
            openingBalanceAdjustmentSchema(),
            correctionAdjustmentSchema(),
            reversalAdjustmentSchema()));
  }

  static Map<String, Object> lineSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "One balanced journal line.", MachineContractPostEntryFieldSpecs.lineFields());
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

  static Map<String, Object> sourceDocumentSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "One retained source document linked to this posting.",
        MachineContractPostEntryFieldSpecs.sourceDocumentFields());
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

  static Map<String, Object> cashRevenueSchema() {
    return typedEventSchema(
        BookkeepingEntryKind.CASH_REVENUE,
        "Typed bookkeeping event for cash-settled revenue recognized immediately into one revenue account.",
        "This request records one cash-revenue business event.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account debited by this business event.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account debited by this business event.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
            "Declared revenue account credited by this business event.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared revenue account credited by this business event.")));
  }

  static Map<String, Object> cashExpenseSchema() {
    return typedEventSchema(
        BookkeepingEntryKind.CASH_EXPENSE,
        "Typed bookkeeping event for one cash-settled expense recognized immediately into one expense account.",
        "This request records one cash-expense business event.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
            "Declared expense account debited by this business event.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared expense account debited by this business event.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this business event.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account credited by this business event.")));
  }

  static Map<String, Object> equityContributionSchema() {
    return typedEventSchema(
        BookkeepingEntryKind.EQUITY_CONTRIBUTION,
        "Typed bookkeeping event for one equity contribution paid into cash.",
        "This request records one equity-contribution business event.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account debited by this contribution.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account debited by this contribution.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            "Declared equity account credited by this contribution.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared equity account credited by this contribution.")));
  }

  static Map<String, Object> equityWithdrawalSchema() {
    return typedEventSchema(
        BookkeepingEntryKind.EQUITY_WITHDRAWAL,
        "Typed bookkeeping event for one equity withdrawal paid out of cash.",
        "This request records one equity-withdrawal business event.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            "Declared equity account debited by this withdrawal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared equity account debited by this withdrawal.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this withdrawal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account credited by this withdrawal.")));
  }

  static Map<String, Object> openingBalanceAdjustmentSchema() {
    return administrativeAdjustmentSchema(
        BookkeepingEntryKind.OPENING_BALANCE_ADJUSTMENT,
        "Explicit administrative opening-balance entry used to seed one book before operating activity begins.",
        "This request records one opening-balance administrative entry.",
        "Balanced non-empty array of journal lines for one opening-balance administrative entry.");
  }

  static Map<String, Object> correctionAdjustmentSchema() {
    return administrativeAdjustmentSchema(
        BookkeepingEntryKind.CORRECTION_ADJUSTMENT,
        "Explicit administrative correction entry for non-opening, non-reversal adjustments outside the typed business-event family.",
        "This request records one correction administrative entry.",
        "Balanced non-empty array of journal lines for one correction administrative entry.");
  }

  static Map<String, Object> reversalAdjustmentSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Explicit administrative reversal entry that fully negates one previously committed posting.",
        List.of(
            requiredEntryKindField(
                BookkeepingEntryKind.REVERSAL_ADJUSTMENT,
                "This request records one reversal administrative entry."),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.LINES,
                "Balanced non-empty array of journal lines for one reversal administrative entry.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of journal lines for one reversal administrative entry.",
                    lineSchema(),
                    2)),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.REVERSAL,
                "Required reversal target descriptor for one reversal administrative entry.",
                reversalSchema())));
  }

  private static Map<String, Object> typedEventSchema(
      BookkeepingEntryKind kind,
      String description,
      String entryKindDescription,
      MachineContractFieldSpec debitOrSourceAccountField,
      MachineContractFieldSpec creditOrTargetAccountField) {
    return MachineContractSchemaSupport.objectSchema(
        description,
        List.of(
            requiredEntryKindField(kind, entryKindDescription),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            debitOrSourceAccountField,
            creditOrTargetAccountField,
            MachineContractPostEntryFieldSpecs.requiredAmountField(),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField()));
  }

  private static Map<String, Object> administrativeAdjustmentSchema(
      BookkeepingEntryKind kind,
      String description,
      String entryKindDescription,
      String linesDescription) {
    return MachineContractSchemaSupport.objectSchema(
        description,
        List.of(
            requiredEntryKindField(kind, entryKindDescription),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.LINES,
                linesDescription,
                MachineContractSchemaSupport.arraySchema(linesDescription, lineSchema(), 2)),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField()));
  }

  private static MachineContractFieldSpec requiredEntryKindField(
      BookkeepingEntryKind kind, String description) {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
        description,
        MachineContractScalarSchemas.constSchema(kind.wireValue(), description));
  }
}
