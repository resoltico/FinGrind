package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;

/** Field specifications for machine-readable post-entry requests. */
final class MachineContractPostEntryFieldSpecs {
  private MachineContractPostEntryFieldSpecs() {}

  static List<MachineContractFieldSpec> topLevelFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
            "Caller-authored bookkeeping entry kind. FinGrind accepts direct journals and typed entries for sales, purchases, inventory capitalization, write-down, shrinkage, count increase, expenses, settlements, owner movements, opening position, and reversal.",
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
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared cash account used by a cash-settled operational entry or owner movement.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
            "Declared trade receivable account used by a sale-on-credit or receipt entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared trade receivable account used by a sale-on-credit or receipt entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
            "Declared trade payable account used by an expense-on-credit or payment entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared trade payable account used by an expense-on-credit or payment entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
            "Declared revenue account credited by a sale entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared revenue account credited by a sale entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
            "Declared inventory asset account used by a typed inventory entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared inventory asset account used by a typed inventory entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
            "Declared expense account debited by an expense entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared expense account debited by an expense entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.WRITE_DOWN_LOSS_ACCOUNT_CODE,
            "Declared expense account debited by an inventory write-down.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared expense account debited by an inventory write-down.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.SHRINKAGE_LOSS_ACCOUNT_CODE,
            "Declared expense account debited by inventory shrinkage.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared expense account debited by inventory shrinkage.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.COUNT_GAIN_ACCOUNT_CODE,
            "Declared revenue account credited by an inventory count increase.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared revenue account credited by an inventory count increase.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            "Declared equity account used by an owner contribution or owner withdrawal entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared equity account used by an owner contribution or owner withdrawal entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.AMOUNT,
            "Exact positive pre-VAT money object carried by an amount-based typed business entry.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive pre-VAT money object carried by an amount-based typed business entry.",
                true)),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.QUANTITY,
            "Exact positive inventory quantity text carried by a quantity-changing inventory entry.",
            MachineContractScalarSchemas.quantityTextSchema(
                "Exact positive inventory quantity text carried by a quantity-changing inventory entry.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.UNIT_COST,
            "Exact positive functional-currency pre-VAT unit cost carried by an inventory acquisition or count increase.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive functional-currency pre-VAT unit cost carried by an inventory acquisition or count increase.",
                true)),
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
            "Optional declared tax selector used by a sale, expense, purchase, or inventory capitalization when this request must resolve through an owned tax registration.",
            MachineContractPostEntryComponentSchemas.taxSchema()),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.LINES,
            "Balanced non-empty array of journal lines used only by direct-journal entries. Inventory accounts are not admitted here because raw journals do not own exact inventory quantity truth.",
            MachineContractSchemaSupport.arraySchema(
                "Balanced non-empty array of journal lines used only by direct-journal entries. Inventory accounts are not admitted here because raw journals do not own exact inventory quantity truth.",
                MachineContractPostEntryComponentSchemas.lineSchema(),
                2)),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.OPENING_BALANCES,
            "Balanced non-empty array of opening balances used only by opening-position entries. Inventory balances carry exact quantity alongside carrying cost; non-inventory balances omit quantity.",
            MachineContractSchemaSupport.arraySchema(
                "Balanced non-empty array of opening balances used only by opening-position entries. Inventory balances carry exact quantity alongside carrying cost; non-inventory balances omit quantity.",
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
}
