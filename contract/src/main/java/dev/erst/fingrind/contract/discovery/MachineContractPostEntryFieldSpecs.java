package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.stream.Stream;

/** Field specifications for machine-readable post-entry requests. */
final class MachineContractPostEntryFieldSpecs {
  private MachineContractPostEntryFieldSpecs() {}

  static List<MachineContractFieldSpec> topLevelFields() {
    return Stream.of(
            identityFields(),
            primaryAccountRoleFields(),
            accrualCutoffFields(),
            remainingAccountRoleFields(),
            monetaryFields(),
            componentFields())
        .flatMap(List::stream)
        .toList();
  }

  private static List<MachineContractFieldSpec> identityFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolBusinessEventFields.Core.ENTRY_KIND,
            "Caller-authored bookkeeping entry kind. FinGrind accepts direct journals and published command-specific typed variants; use machine discovery or command help for each variant's exact requirements.",
            MachineContractScalarSchemas.enumStringSchema(
                "Caller-authored bookkeeping entry kind.", BookkeepingEntryKind.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
            "ISO-8601 local date that makes the bookkeeping entry effective.",
            MachineContractScalarSchemas.dateStringSchema(
                "ISO-8601 local date that makes the bookkeeping entry effective.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
            "Declared cash account used by a cash-settled operational entry or owner movement.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared cash account used by a cash-settled operational entry or owner movement.")));
  }

  private static List<MachineContractFieldSpec> primaryAccountRoleFields() {
    return List.of(
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.RECEIVABLE_ACCOUNT_CODE,
            "Declared trade receivable account used by a sale-on-credit or receipt entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared trade receivable account used by a sale-on-credit or receipt entry.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.PAYABLE_ACCOUNT_CODE,
            "Declared trade payable account used by an expense-on-credit or payment entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared trade payable account used by an expense-on-credit or payment entry.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE,
            "Declared revenue account credited by a sale entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared revenue account credited by a sale entry.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID,
            "Stable caller-chosen identifier for one prepayment, deferred-revenue, or accrued-expense lifecycle.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Stable caller-chosen identifier for one accrual cut-off lifecycle.",
                AccrualCutoffId.pattern(),
                AccrualCutoffId.maxLength())));
  }

  private static List<MachineContractFieldSpec> accrualCutoffFields() {
    return List.of(
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.AccrualCutoff.PREPAYMENT_ASSET_ACCOUNT_CODE,
            "Declared prepaid-expense asset account debited by a prepayment.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared prepaid-expense asset account debited by a prepayment.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.AccrualCutoff.DEFERRED_REVENUE_ACCOUNT_CODE,
            "Declared deferred-revenue liability account credited by a deferred-revenue receipt.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared deferred-revenue liability account credited by a deferred-revenue receipt.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.AccrualCutoff.ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE,
            "Declared accrued-expense liability account credited by an accrued expense.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared accrued-expense liability account credited by an accrued expense.")));
  }

  private static List<MachineContractFieldSpec> remainingAccountRoleFields() {
    return List.of(
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
            "Declared inventory asset account used by a typed inventory entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared inventory asset account used by a typed inventory entry.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE,
            "Declared expense account debited by an expense entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared expense account debited by an expense entry.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Inventory.WRITE_DOWN_LOSS_ACCOUNT_CODE,
            "Declared expense account debited by an inventory write-down.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared expense account debited by an inventory write-down.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Inventory.SHRINKAGE_LOSS_ACCOUNT_CODE,
            "Declared expense account debited by inventory shrinkage.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared expense account debited by inventory shrinkage.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Inventory.COUNT_GAIN_ACCOUNT_CODE,
            "Declared revenue account credited by an inventory count increase.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared revenue account credited by an inventory count increase.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.EQUITY_ACCOUNT_CODE,
            "Declared equity account used by an owner contribution or owner withdrawal entry.",
            MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(
                "Declared equity account used by an owner contribution or owner withdrawal entry.")));
  }

  private static List<MachineContractFieldSpec> monetaryFields() {
    return List.of(
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.AMOUNT,
            "Exact positive pre-VAT money object carried by an amount-based typed business entry.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive pre-VAT money object carried by an amount-based typed business entry.",
                true)),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Inventory.QUANTITY,
            "Exact positive inventory quantity text carried by a quantity-changing inventory entry.",
            MachineContractScalarSchemas.quantityTextSchema(
                "Exact positive inventory quantity text carried by a quantity-changing inventory entry.")),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Inventory.UNIT_COST,
            "Exact positive functional-currency pre-VAT unit cost carried by an inventory acquisition or count increase.",
            MachineContractScalarSchemas.moneyObjectSchema(
                "Exact positive functional-currency pre-VAT unit cost carried by an inventory acquisition or count increase.",
                true)),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL,
            "Inclusive date interval in which an admitted prepayment or deferred-revenue balance may be recognized.",
            MachineContractPostEntryComponentSchemas.recognitionIntervalSchema()),
        MachineContractFieldSpec.conditional(
            ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF,
            "Conditional inventory-relief facts paired with a sale entry. Trading-template sale requests require this object so one committed sale can carry both revenue recognition and cost-of-sales relief.",
            MachineContractPostEntryComponentSchemas.inventoryReliefSchema()));
  }

  private static List<MachineContractFieldSpec> componentFields() {
    return List.of(
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.SETTLEMENT_ADJUNCT,
            "Optional settlement-side adjunct used by a receipt or payment entry.",
            MachineContractPostEntryComponentSchemas.settlementAdjunctSchema()),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
            "Optional owned foreign-exchange facts for a transaction-currency event translated into book functional currency.",
            MachineContractPostEntryComponentSchemas.foreignExchangeSchema()),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.TAX,
            "Optional declared tax selector used by a sale, expense, purchase, or inventory capitalization when this request must resolve through an owned tax registration.",
            MachineContractPostEntryComponentSchemas.taxSchema()),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.LINES,
            "Balanced non-empty array of journal lines used only by direct-journal entries. Inventory accounts are not admitted here because raw journals do not own exact inventory quantity truth.",
            MachineContractSchemaSupport.arraySchema(
                "Balanced non-empty array of journal lines used only by direct-journal entries. Inventory accounts are not admitted here because raw journals do not own exact inventory quantity truth.",
                MachineContractPostEntryComponentSchemas.lineSchema(),
                2)),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.OPENING_BALANCES,
            "Balanced non-empty array of opening balances used only by opening-position entries. Inventory balances carry exact quantity alongside carrying cost; non-inventory balances omit quantity.",
            MachineContractSchemaSupport.arraySchema(
                "Balanced non-empty array of opening balances used only by opening-position entries. Inventory balances carry exact quantity alongside carrying cost; non-inventory balances omit quantity.",
                MachineContractPostEntryComponentSchemas.openingBalanceSchema(),
                2)),
        MachineContractFieldSpec.required(
            ProtocolBusinessEventFields.Core.EVIDENCE,
            "First-class retained source-document and approval evidence linked to this bookkeeping entry.",
            MachineContractPostEntryComponentSchemas.evidenceSchema()),
        MachineContractFieldSpec.required(
            ProtocolBusinessEventFields.Core.PROVENANCE,
            "Caller-supplied request provenance captured before commit.",
            MachineContractPostEntryComponentSchemas.provenanceSchema()),
        MachineContractFieldSpec.optional(
            ProtocolBusinessEventFields.Core.REVERSAL,
            "Required reversal target descriptor for REVERSAL entries and absent otherwise.",
            MachineContractPostEntryComponentSchemas.reversalSchema()));
  }
}
