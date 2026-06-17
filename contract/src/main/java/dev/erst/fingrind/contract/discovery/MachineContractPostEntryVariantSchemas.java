package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
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
            journalDirectSchema(),
            cashRevenueRecipeSchema(),
            cashExpenseRecipeSchema(),
            equityContributionRecipeSchema(),
            equityWithdrawalRecipeSchema(),
            openAccountingPositionSchema(),
            reversalAdjustmentSchema()));
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

  static Map<String, Object> sourceDocumentSchema() {
    return MachineContractPostEntryComponentSchemas.sourceDocumentSchema();
  }

  static Map<String, Object> approvalSchema() {
    return MachineContractPostEntryComponentSchemas.approvalSchema();
  }

  static Map<String, Object> reversalSchema() {
    return MachineContractPostEntryComponentSchemas.reversalSchema();
  }

  static Map<String, Object> journalDirectSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Direct balanced operational journal written without a higher-level recipe.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.JOURNAL, "This request records one direct balanced journal."),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.LINES,
                "Balanced non-empty array of journal lines for one direct journal.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of journal lines for one direct journal.",
                    lineSchema(),
                    2)),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField()));
  }

  static Map<String, Object> cashRevenueRecipeSchema() {
    return recipeSchema(
        JournalRecipeKind.CASH_REVENUE,
        "Recipe-backed journal that debits one asset cash account and credits one revenue account.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account debited by this recipe-backed journal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account debited by this recipe-backed journal.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
            "Declared revenue account credited by this recipe-backed journal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared revenue account credited by this recipe-backed journal.")));
  }

  static Map<String, Object> cashExpenseRecipeSchema() {
    return recipeSchema(
        JournalRecipeKind.CASH_EXPENSE,
        "Recipe-backed journal that debits one expense account and credits one asset cash account.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
            "Declared expense account debited by this recipe-backed journal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared expense account debited by this recipe-backed journal.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this recipe-backed journal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account credited by this recipe-backed journal.")));
  }

  static Map<String, Object> equityContributionRecipeSchema() {
    return recipeSchema(
        JournalRecipeKind.EQUITY_CONTRIBUTION,
        "Recipe-backed journal that debits one asset cash account and credits one equity account.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account debited by this recipe-backed journal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account debited by this recipe-backed journal.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            "Declared equity account credited by this recipe-backed journal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared equity account credited by this recipe-backed journal.")));
  }

  static Map<String, Object> equityWithdrawalRecipeSchema() {
    return recipeSchema(
        JournalRecipeKind.EQUITY_WITHDRAWAL,
        "Recipe-backed journal that debits one equity account and credits one asset cash account.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            "Declared equity account debited by this recipe-backed journal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared equity account debited by this recipe-backed journal.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this recipe-backed journal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account credited by this recipe-backed journal.")));
  }

  static Map<String, Object> openAccountingPositionSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Explicit administrative opening-position entry used to seed one book before operating activity begins.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION,
                "This request records one opening-position administrative entry."),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.OPENING_BALANCES,
                "Balanced non-empty array of opening balances for one opening-position administrative entry.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of opening balances for one opening-position administrative entry.",
                    openingBalanceSchema(),
                    2)),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField()));
  }

  static Map<String, Object> reversalAdjustmentSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Explicit administrative reversal entry that fully negates one previously committed posting.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
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

  private static Map<String, Object> recipeSchema(
      JournalRecipeKind recipeKind,
      String description,
      MachineContractFieldSpec debitOrSourceAccountField,
      MachineContractFieldSpec creditOrTargetAccountField) {
    return MachineContractSchemaSupport.objectSchema(
        description,
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.JOURNAL,
                "This request records one journal backed by a named convenience recipe."),
            MachineContractPostEntryComponentSchemas.requiredRecipeKindField(
                recipeKind, "Selected journal recipe for this request."),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            debitOrSourceAccountField,
            creditOrTargetAccountField,
            MachineContractPostEntryFieldSpecs.requiredAmountField(),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField()));
  }
}
