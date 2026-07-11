package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Typed business-entry schema builders for the post-entry request family. */
final class MachineContractPostEntryTypedVariantSchemaBuilders {
  private MachineContractPostEntryTypedVariantSchemaBuilders() {}

  static Map<String, Object> saleSettledSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.SALE_SETTLED,
        "Settled sale entry that debits a cash account and credits a revenue account.",
        true,
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account debited by this settled sale."),
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
            "Declared revenue account credited by this settled sale."),
        null,
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF,
            "Optional inventory-relief facts for this sale. Trading-template books require this object so one sale request carries both revenue and cost-of-sales recognition.",
            MachineContractPostEntryComponentSchemas.inventoryReliefSchema()),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.TAX,
            "Optional declared tax selector that resolves this settled sale through an owned tax registration.",
            MachineContractPostEntryComponentSchemas.taxSchema()));
  }

  static Map<String, Object> saleOnCreditSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.SALE_ON_CREDIT,
        "Sale-on-credit entry that debits a trade receivable account and credits a revenue account.",
        true,
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
            "Declared trade receivable account debited by this sale-on-credit."),
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
            "Declared revenue account credited by this sale-on-credit."),
        null,
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF,
            "Optional inventory-relief facts for this sale-on-credit. Trading-template books require this object so one sale request carries both revenue and cost-of-sales recognition.",
            MachineContractPostEntryComponentSchemas.inventoryReliefSchema()),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.TAX,
            "Optional declared tax selector that resolves this sale-on-credit through an owned tax registration.",
            MachineContractPostEntryComponentSchemas.taxSchema()));
  }

  static Map<String, Object> expenseSettledSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.EXPENSE_SETTLED,
        "Settled expense entry that debits an expense account and credits a cash account.",
        true,
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
            "Declared expense account debited by this settled expense."),
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this settled expense."),
        null,
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.TAX,
            "Optional declared tax selector that resolves this settled expense through an owned tax registration.",
            MachineContractPostEntryComponentSchemas.taxSchema()));
  }

  static Map<String, Object> expenseOnCreditSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.EXPENSE_ON_CREDIT,
        "Expense-on-credit entry that debits an expense account and credits a trade payable account.",
        true,
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
            "Declared expense account debited by this expense-on-credit."),
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
            "Declared trade payable account credited by this expense-on-credit."),
        null,
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.TAX,
            "Optional declared tax selector that resolves this expense-on-credit through an owned tax registration.",
            MachineContractPostEntryComponentSchemas.taxSchema()));
  }

  static Map<String, Object> receiptSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.RECEIPT,
        "Receipt entry that settles a trade receivable into cash and may carry a settlement adjunct.",
        false,
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account debited by this receipt."),
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
            "Declared trade receivable account credited by this receipt."),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT,
            "Optional settlement adjunct used by this receipt.",
            MachineContractPostEntryComponentSchemas.settlementAdjunctSchema()));
  }

  static Map<String, Object> paymentSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.PAYMENT,
        "Payment entry that settles a trade payable from cash and may carry a settlement adjunct.",
        false,
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
            "Declared trade payable account debited by this payment."),
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this payment."),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT,
            "Optional settlement adjunct used by this payment.",
            MachineContractPostEntryComponentSchemas.settlementAdjunctSchema()));
  }

  static Map<String, Object> ownerContributionSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.OWNER_CONTRIBUTION,
        "Owner-contribution entry that debits an asset cash account and credits an equity account.",
        true,
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account debited by this owner contribution."),
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            "Declared equity account credited by this owner contribution."),
        null);
  }

  static Map<String, Object> ownerWithdrawalSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.OWNER_WITHDRAWAL,
        "Owner-withdrawal entry that debits an equity account and credits an asset cash account.",
        true,
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            "Declared equity account debited by this owner withdrawal."),
        requiredAccountField(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this owner withdrawal."),
        null);
  }

  static Map<String, Object> roleAmountSchema(
      BookkeepingEntryKind entryKind,
      String description,
      boolean includeForeignExchange,
      MachineContractFieldSpec debitOrSourceAccountField,
      MachineContractFieldSpec creditOrTargetAccountField,
      @Nullable MachineContractFieldSpec settlementAdjunctField,
      MachineContractFieldSpec... additionalFields) {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts = entryKindFacts(entryKind);
    List<MachineContractFieldSpec> fields = new java.util.ArrayList<>();
    fields.add(
        MachineContractPostEntryComponentSchemas.requiredEntryKindField(
            entryKind, "This request records a typed business entry."));
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredEffectiveDateField());
    fields.add(debitOrSourceAccountField);
    fields.add(creditOrTargetAccountField);
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredAmountField());
    if (settlementAdjunctField != null) {
      fields.add(settlementAdjunctField);
    }
    if (includeForeignExchange) {
      fields.add(
          MachineContractFieldSpec.optional(
              ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
              "Optional owned foreign-exchange facts for a transaction-currency event translated into book functional currency.",
              MachineContractPostEntryComponentSchemas.foreignExchangeSchema()));
    }
    java.util.Collections.addAll(fields, additionalFields);
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredEvidenceField(entryKindFacts));
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredProvenanceField());
    return MachineContractSchemaSupport.objectSchema(description, List.copyOf(fields));
  }

  static RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts(
      BookkeepingEntryKind entryKind) {
    return ProtocolCatalog.domain().requestSurface().bookkeepingEntryKind(entryKind);
  }

  static MachineContractFieldSpec requiredAccountField(String fieldName, String description) {
    return MachineContractFieldSpec.required(
        fieldName,
        description,
        MachineContractPostEntryRequiredFieldSpecs.accountCodeSchema(description));
  }
}
