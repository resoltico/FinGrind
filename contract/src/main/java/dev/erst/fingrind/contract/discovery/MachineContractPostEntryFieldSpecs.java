package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import java.util.Map;

/** Field specifications for machine-readable post-entry requests. */
final class MachineContractPostEntryFieldSpecs {
  private MachineContractPostEntryFieldSpecs() {}

  static List<MachineContractFieldSpec> topLevelFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
            "Caller-authored bookkeeping entry kind. FinGrind accepts direct journals and typed business entries for settled sale, sale on credit, settled expense, expense on credit, receipt, payment, owner contribution, owner withdrawal, opening position, and reversal.",
            MachineContractScalarSchemas.enumStringSchema(
                "Caller-authored bookkeeping entry kind.", BookkeepingEntryKind.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
            "ISO-8601 local date that makes the bookkeeping entry effective.",
            MachineContractScalarSchemas.dateStringSchema(
                "ISO-8601 local date that makes the bookkeeping entry effective.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account used by a cash-settled operational entry or owner movement.",
            accountCodeSchema(
                "Declared cash account used by a cash-settled operational entry or owner movement.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
            "Declared trade receivable account used by a sale-on-credit or receipt entry.",
            accountCodeSchema(
                "Declared trade receivable account used by a sale-on-credit or receipt entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
            "Declared trade payable account used by an expense-on-credit or payment entry.",
            accountCodeSchema(
                "Declared trade payable account used by an expense-on-credit or payment entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
            "Declared revenue account credited by a sale entry.",
            accountCodeSchema("Declared revenue account credited by a sale entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
            "Declared inventory asset account used by one trading-template inventory-purchase entry.",
            accountCodeSchema(
                "Declared inventory asset account used by one trading-template inventory-purchase entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
            "Declared expense account debited by an expense entry.",
            accountCodeSchema("Declared expense account debited by an expense entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            "Declared equity account used by an owner contribution or owner withdrawal entry.",
            accountCodeSchema(
                "Declared equity account used by an owner contribution or owner withdrawal entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.AMOUNT,
            "Exact positive money object carried by a typed business entry.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive money object carried by a typed business entry.", true)),
        MachineContractFieldSpec.conditional(
            ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF,
            "Conditional inventory-relief facts paired with a sale entry. Trading-template sale requests require this object so one committed sale can carry both revenue recognition and cost-of-sales relief.",
            MachineContractPostEntryComponentSchemas.inventoryReliefSchema()),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT,
            "Optional settlement-side adjunct used by a receipt or payment entry.",
            MachineContractPostEntryComponentSchemas.settlementAdjunctSchema()),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
            "Optional owned foreign-exchange facts for a transaction-currency event translated into book functional currency.",
            MachineContractPostEntryComponentSchemas.foreignExchangeSchema()),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.TAX,
            "Optional declared tax selector used by sale and expense entries when this request must resolve through an owned tax registration.",
            MachineContractPostEntryComponentSchemas.taxSchema()),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.LINES,
            "Balanced non-empty array of journal lines used only by direct-journal entries.",
            MachineContractSchemaSupport.arraySchema(
                "Balanced non-empty array of journal lines used only by direct-journal entries.",
                MachineContractPostEntryComponentSchemas.lineSchema(),
                2)),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.OPENING_BALANCES,
            "Balanced non-empty array of opening balances used only by opening-position entries.",
            MachineContractSchemaSupport.arraySchema(
                "Balanced non-empty array of opening balances used only by opening-position entries.",
                MachineContractPostEntryComponentSchemas.openingBalanceSchema(),
                2)),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EVIDENCE,
            "First-class retained source-document and approval evidence linked to this bookkeeping entry.",
            MachineContractPostEntryComponentSchemas.evidenceSchema()),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.PROVENANCE,
            "Caller-supplied request provenance captured before commit.",
            MachineContractPostEntryComponentSchemas.provenanceSchema()),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.REVERSAL,
            "Required reversal target descriptor for REVERSAL entries and absent otherwise.",
            MachineContractPostEntryComponentSchemas.reversalSchema()));
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
            "Exact positive money object for this journal line. Every line in the same entry must resolve to the same currency unit.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive money object for this journal line.", true)));
  }

  static List<MachineContractFieldSpec> openingBalanceFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.OpeningBalance.ACCOUNT_CODE,
            "Declared book-local account code for this opening balance.",
            accountCodeSchema("Declared book-local account code for this opening balance.")),
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
                "Exact positive money object for this opening balance.", true)));
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
            accountCodeSchema("Declared adjunct account used by a receipt or payment settlement.")),
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
            accountCodeSchema(
                "Declared non-cash inventory account credited by this trading-sale inventory relief.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.InventoryRelief.COST_OF_SALES_ACCOUNT_CODE,
            "Declared cost-of-sales account debited by this trading-sale inventory relief.",
            accountCodeSchema(
                "Declared cost-of-sales account debited by this trading-sale inventory relief.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.InventoryRelief.AMOUNT,
            "Exact positive money object carried by the inventory-relief leg of a trading sale.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive money object carried by the inventory-relief leg of a trading sale.",
                true)));
  }

  static List<MachineContractFieldSpec> evidenceFields() {
    return MachineContractPostEntryEvidenceFieldSpecs.evidenceFields();
  }

  static List<MachineContractFieldSpec> sourceDocumentFields() {
    return MachineContractPostEntryEvidenceFieldSpecs.sourceDocumentFields();
  }

  static List<MachineContractFieldSpec> sourceDocumentFields(
      RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts) {
    return MachineContractPostEntryEvidenceFieldSpecs.sourceDocumentFields(entryKindFacts);
  }

  static List<MachineContractFieldSpec> approvalFields() {
    return MachineContractPostEntryEvidenceFieldSpecs.approvalFields();
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
        "Exact positive money object carried by this typed business entry.",
        MachineContractScalarSchemas.moneyObjectSchema(
            "Exact positive money object carried by this typed business entry.", true));
  }

  static MachineContractFieldSpec requiredEvidenceField(
      RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts) {
    return MachineContractFieldSpec.required(
        ProtocolPostEntryFields.TopLevel.EVIDENCE,
        "First-class retained source-document and approval evidence linked to this bookkeeping entry.",
        MachineContractPostEntryVariantSchemas.evidenceSchema(entryKindFacts));
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
