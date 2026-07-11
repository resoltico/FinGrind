package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.jspecify.annotations.Nullable;

/** Typed business-entry request templates for the post-entry discovery surface. */
final class MachineContractPostEntryTypedVariantTemplates {
  private MachineContractPostEntryTypedVariantTemplates() {}

  static ContractTemplates.PostingRequestTemplateDescriptor saleSettledTemplate(
      @Nullable BookTemplateId bookTemplateId) {
    return MachineContractPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.SALE_SETTLED,
        "cash",
        null,
        null,
        MachineContractPostEntryVariantTemplates.salesRevenueAccountCode(bookTemplateId),
        null,
        null,
        null,
        MachineContractPostEntryVariantTemplates.tradingInventoryRelief(bookTemplateId));
  }

  static ContractTemplates.PostingRequestTemplateDescriptor saleOnCreditTemplate(
      @Nullable BookTemplateId bookTemplateId) {
    return MachineContractPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.SALE_ON_CREDIT,
        null,
        "accounts-receivable",
        null,
        MachineContractPostEntryVariantTemplates.salesRevenueAccountCode(bookTemplateId),
        null,
        null,
        null,
        MachineContractPostEntryVariantTemplates.tradingInventoryRelief(bookTemplateId));
  }

  static ContractTemplates.PostingRequestTemplateDescriptor purchaseSettledTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.purchaseTemplate(
        BookkeepingEntryKind.PURCHASE_SETTLED, "cash", null, null, null, "inventory", null, null);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor purchaseOnCreditTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.purchaseTemplate(
        BookkeepingEntryKind.PURCHASE_ON_CREDIT,
        null,
        null,
        "accounts-payable",
        null,
        "inventory",
        null,
        null);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor inventoryCapitalizationSettledTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED, "cash", null, null);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor inventoryCapitalizationOnCreditTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT, null, "accounts-payable", null);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor inventoryWriteDownTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.INVENTORY_WRITE_DOWN, null, null, "inventory-write-down-loss");
  }

  static ContractTemplates.PostingRequestTemplateDescriptor inventoryShrinkageTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.shrinkageTemplate(
        BookkeepingEntryKind.INVENTORY_SHRINKAGE);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor inventoryCountIncreaseTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.countIncreaseTemplate(
        BookkeepingEntryKind.INVENTORY_COUNT_INCREASE);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor expenseSettledTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.EXPENSE_SETTLED,
        "cash",
        null,
        null,
        null,
        null,
        "operating-expense",
        null,
        null);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor expenseOnCreditTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.EXPENSE_ON_CREDIT,
        null,
        null,
        "accounts-payable",
        null,
        null,
        "operating-expense",
        null,
        null);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor receiptTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.RECEIPT,
        "cash",
        "accounts-receivable",
        null,
        null,
        null,
        null,
        null,
        null);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor paymentTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.PAYMENT,
        "cash",
        null,
        "accounts-payable",
        null,
        null,
        null,
        null,
        null);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor ownerContributionTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.OWNER_CONTRIBUTION,
        "cash",
        null,
        null,
        null,
        null,
        null,
        "owner-capital",
        null);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor ownerWithdrawalTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.OWNER_WITHDRAWAL,
        "cash",
        null,
        null,
        null,
        null,
        null,
        "owner-draws",
        null);
  }
}
