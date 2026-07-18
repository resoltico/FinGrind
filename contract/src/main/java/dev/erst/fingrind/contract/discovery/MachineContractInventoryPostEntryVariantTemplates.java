package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.jspecify.annotations.Nullable;

/** Inventory acquisition and maintenance request templates for the discovery surface. */
final class MachineContractInventoryPostEntryVariantTemplates {
  private static final String SAMPLE_EFFECTIVE_DATE = "2026-01-15";
  private static final String SAMPLE_QUANTITY = "5";
  private static final MonetaryAmount SAMPLE_UNIT_COST = new MonetaryAmount("EUR", "120");

  private MachineContractInventoryPostEntryVariantTemplates() {}

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor purchaseTemplate(
      BookkeepingEntryKind entryKind,
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode) {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        SAMPLE_EFFECTIVE_DATE,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        null,
        null,
        null,
        equityAccountCode,
        null,
        SAMPLE_QUANTITY,
        SAMPLE_UNIT_COST,
        null,
        null,
        null,
        null,
        null,
        null,
        MachineContractPostEntryVariantTemplates.evidenceTemplate(entryKind),
        MachineContractPostEntryVariantTemplates.provenanceTemplate(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor roleAmountTemplate(
      BookkeepingEntryKind entryKind,
      @Nullable String cashAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String writeDownLossAccountCode) {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        SAMPLE_EFFECTIVE_DATE,
        cashAccountCode,
        null,
        payableAccountCode,
        null,
        "inventory",
        null,
        writeDownLossAccountCode,
        null,
        null,
        null,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        MachineContractPostEntryVariantTemplates.evidenceTemplate(entryKind),
        MachineContractPostEntryVariantTemplates.provenanceTemplate(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor shrinkageTemplate(
      BookkeepingEntryKind entryKind) {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        SAMPLE_EFFECTIVE_DATE,
        null,
        null,
        null,
        null,
        "inventory",
        null,
        null,
        "inventory-shrinkage-loss",
        null,
        null,
        null,
        SAMPLE_QUANTITY,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        MachineContractPostEntryVariantTemplates.evidenceTemplate(entryKind),
        MachineContractPostEntryVariantTemplates.provenanceTemplate(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor countIncreaseTemplate(
      BookkeepingEntryKind entryKind) {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        SAMPLE_EFFECTIVE_DATE,
        null,
        null,
        null,
        null,
        "inventory",
        null,
        null,
        null,
        "inventory-count-gain",
        null,
        null,
        SAMPLE_QUANTITY,
        SAMPLE_UNIT_COST,
        null,
        null,
        null,
        null,
        null,
        null,
        MachineContractPostEntryVariantTemplates.evidenceTemplate(entryKind),
        MachineContractPostEntryVariantTemplates.provenanceTemplate(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
