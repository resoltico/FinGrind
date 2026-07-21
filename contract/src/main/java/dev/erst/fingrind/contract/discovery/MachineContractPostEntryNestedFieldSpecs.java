package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolFixedAssetRequestFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import java.util.Map;

/** Nested component field sets for post-entry machine contracts. */
final class MachineContractPostEntryNestedFieldSpecs {
  private MachineContractPostEntryNestedFieldSpecs() {}

  static List<MachineContractFieldSpec> lineFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE,
            "Declared book-local account code for this journal line. FinGrind accepts ASCII letters or digits followed by ASCII letters, digits, '.', '_', ':', '/', or '-'.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared book-local account code for this journal line.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.SIDE,
            "Journal side that carries the line amount.",
            MachineContractScalarSchemas.enumStringSchema(
                "Journal side that carries the line amount.", JournalLine.EntrySide.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.JournalLine.AMOUNT,
            "Exact positive money object for this journal line. Every line in the same entry must resolve to the same currency unit.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive money object for this journal line.", true)));
  }

  static List<MachineContractFieldSpec> openingBalanceFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.OpeningBalance.ACCOUNT_CODE,
            "Declared book-local account code for this opening balance.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared book-local account code for this opening balance.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.OpeningBalance.SIDE,
            "Journal side that carries the opening balance amount.",
            MachineContractScalarSchemas.enumStringSchema(
                "Journal side that carries the opening balance amount.",
                JournalLine.EntrySide.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.OpeningBalance.AMOUNT,
            "Exact positive money object for this opening balance.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive money object for this opening balance.", true)),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.OpeningBalance.QUANTITY,
            "Exact inventory quantity required when this balance names an inventory account and absent otherwise.",
            MachineContractScalarSchemas.quantityTextSchema(
                "Exact inventory quantity required when this balance names an inventory account and absent otherwise.")));
  }

  static List<MachineContractFieldSpec> taxFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Tax.TAX_REGISTRATION_ID,
            "Declared tax-registration identifier selected for this entry.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Declared tax-registration identifier selected for this entry.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.Tax.TAX_CODE,
            "Declared tax code selected inside the named tax registration.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Declared tax code selected inside the named tax registration.")));
  }

  static List<MachineContractFieldSpec> settlementAdjunctFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SettlementAdjunct.ACCOUNT_CODE,
            "Declared adjunct account used by a receipt or payment settlement.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared adjunct account used by a receipt or payment settlement.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.SettlementAdjunct.AMOUNT,
            "Exact positive money object carried by a settlement adjunct.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive money object carried by a settlement adjunct.", true)));
  }

  static List<MachineContractFieldSpec> inventoryReliefFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.InventoryRelief.INVENTORY_ACCOUNT_CODE,
            "Declared non-cash inventory account credited by this trading-sale inventory relief.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared non-cash inventory account credited by this trading-sale inventory relief.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.InventoryRelief.COST_OF_SALES_ACCOUNT_CODE,
            "Declared cost-of-sales account debited by this trading-sale inventory relief.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared cost-of-sales account debited by this trading-sale inventory relief.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.InventoryRelief.QUANTITY,
            "Exact positive quantity text carried by the inventory-relief leg of a trading sale.",
            MachineContractScalarSchemas.quantityTextSchema(
                "Exact positive quantity text carried by the inventory-relief leg of a trading sale.")));
  }

  static List<MachineContractFieldSpec> recognitionIntervalFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.RecognitionInterval.START_DATE,
            "Inclusive ISO-8601 local date on which recognition may first occur.",
            MachineContractScalarSchemas.dateStringSchema(
                "Inclusive ISO-8601 local date on which recognition may first occur.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.RecognitionInterval.END_DATE,
            "Inclusive ISO-8601 local date on which recognition may last occur.",
            MachineContractScalarSchemas.dateStringSchema(
                "Inclusive ISO-8601 local date on which recognition may last occur.")));
  }

  static List<MachineContractFieldSpec> fixedAssetDepreciationScheduleFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolFixedAssetRequestFields.DepreciationSchedule.IN_SERVICE_DATE,
            "ISO-8601 local date on which the capitalized asset enters service.",
            MachineContractScalarSchemas.dateStringSchema(
                "ISO-8601 local date on which the capitalized asset enters service.")),
        MachineContractFieldSpec.required(
            ProtocolFixedAssetRequestFields.DepreciationSchedule.USEFUL_LIFE_MONTHS,
            "Positive integer count of straight-line depreciation months.",
            Map.of("type", "integer", "minimum", 1, "maximum", 1200)),
        MachineContractFieldSpec.required(
            ProtocolFixedAssetRequestFields.DepreciationSchedule.RESIDUAL_VALUE,
            "Exact non-negative functional-currency residual value retained at the end of the useful life.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact non-negative functional-currency residual value retained at the end of the useful life.",
                false)));
  }

  static List<MachineContractFieldSpec> provenanceFields() {
    return List.of(
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
}
