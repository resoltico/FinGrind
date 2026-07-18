package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.jspecify.annotations.Nullable;

/** Typed business-entry request templates for the post-entry discovery surface. */
final class MachineContractPostEntryTypedVariantTemplates {
  private MachineContractPostEntryTypedVariantTemplates() {}

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor saleSettledTemplate(
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

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor saleOnCreditTemplate(
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

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor purchaseSettledTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.purchaseTemplate(
        BookkeepingEntryKind.PURCHASE_SETTLED, "cash", null, null, null, "inventory", null, null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor purchaseOnCreditTemplate(
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

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      inventoryCapitalizationSettledTemplate(@Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED, "cash", null, null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      inventoryCapitalizationOnCreditTemplate(@Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT, null, "accounts-payable", null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      inventoryWriteDownTemplate(@Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.roleAmountTemplate(
        BookkeepingEntryKind.INVENTORY_WRITE_DOWN, null, null, "inventory-write-down-loss");
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      inventoryShrinkageTemplate(@Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.shrinkageTemplate(
        BookkeepingEntryKind.INVENTORY_SHRINKAGE);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      inventoryCountIncreaseTemplate(@Nullable BookTemplateId ignoredBookTemplateId) {
    return MachineContractInventoryPostEntryVariantTemplates.countIncreaseTemplate(
        BookkeepingEntryKind.INVENTORY_COUNT_INCREASE);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor expenseSettledTemplate(
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

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor expenseOnCreditTemplate(
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

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor receiptTemplate(
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

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor paymentTemplate(
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

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor ownerContributionTemplate(
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

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor ownerWithdrawalTemplate(
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
