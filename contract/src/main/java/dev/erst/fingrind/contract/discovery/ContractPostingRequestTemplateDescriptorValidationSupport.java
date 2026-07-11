package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.AccountingEvidenceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.ProvenanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.SettlementAdjunctTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.TaxSelectionTemplateDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Validation owner for posting request-template descriptors. */
final class ContractPostingRequestTemplateDescriptorValidationSupport {
  private ContractPostingRequestTemplateDescriptorValidationSupport() {}

  static ValidatedPostingRequestTemplateDescriptor validate(PostingRequestTemplateDraft draft) {
    BookkeepingEntryKind entryKind =
        ContractDescriptorValidation.requireValue(draft.entryKind(), "entryKind");
    String effectiveDate =
        ContractDescriptorValidation.requireText(draft.effectiveDate(), "effectiveDate");
    String cashAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            draft.cashAccountCode(), "cashAccountCode");
    String receivableAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            draft.receivableAccountCode(), "receivableAccountCode");
    String payableAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            draft.payableAccountCode(), "payableAccountCode");
    String revenueAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            draft.revenueAccountCode(), "revenueAccountCode");
    String inventoryAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            draft.inventoryAccountCode(), "inventoryAccountCode");
    String expenseAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            draft.expenseAccountCode(), "expenseAccountCode");
    String writeDownLossAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            draft.writeDownLossAccountCode(), "writeDownLossAccountCode");
    String shrinkageLossAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            draft.shrinkageLossAccountCode(), "shrinkageLossAccountCode");
    String countGainAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            draft.countGainAccountCode(), "countGainAccountCode");
    String equityAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            draft.equityAccountCode(), "equityAccountCode");
    MonetaryAmount amount =
        ContractDescriptorValidation.requireOptionalValue(draft.amount(), "amount");
    String quantity =
        ContractDescriptorValidation.requireOptionalText(draft.quantity(), "quantity");
    MonetaryAmount unitCost =
        ContractDescriptorValidation.requireOptionalValue(draft.unitCost(), "unitCost");
    InventoryReliefTemplateDescriptor inventoryRelief =
        ContractDescriptorValidation.requireOptionalValue(
            draft.inventoryRelief(), "inventoryRelief");
    SettlementAdjunctTemplateDescriptor settlementAdjunct =
        ContractDescriptorValidation.requireOptionalValue(
            draft.settlementAdjunct(), "settlementAdjunct");
    ForeignExchangeTemplateDescriptor foreignExchange =
        ContractDescriptorValidation.requireOptionalValue(
            draft.foreignExchange(), "foreignExchange");
    TaxSelectionTemplateDescriptor tax =
        ContractDescriptorValidation.requireOptionalValue(draft.tax(), "tax");
    List<JournalLineTemplateDescriptor> lines =
        draft.lines() == null
            ? null
            : ContractDescriptorValidation.copyList(draft.lines(), "lines");
    List<OpeningBalanceTemplateDescriptor> openingBalances =
        draft.openingBalances() == null
            ? null
            : ContractDescriptorValidation.copyList(draft.openingBalances(), "openingBalances");
    AccountingEvidenceTemplateDescriptor evidence =
        ContractDescriptorValidation.requireValue(draft.evidence(), "evidence");
    ProvenanceTemplateDescriptor provenance =
        ContractDescriptorValidation.requireValue(draft.provenance(), "provenance");
    ReversalTemplateDescriptor reversal = draft.reversal();
    ContractPostingRequestTemplateValidators.validate(
        entryKind,
        new ContractPostingRequestTemplateValidators.PostingTemplateFields(
            cashAccountCode,
            receivableAccountCode,
            payableAccountCode,
            revenueAccountCode,
            inventoryAccountCode,
            expenseAccountCode,
            writeDownLossAccountCode,
            shrinkageLossAccountCode,
            countGainAccountCode,
            equityAccountCode,
            amount,
            quantity,
            unitCost,
            inventoryRelief,
            settlementAdjunct,
            foreignExchange,
            tax,
            lines,
            openingBalances),
        reversal);
    return new ValidatedPostingRequestTemplateDescriptor(
        entryKind,
        effectiveDate,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        writeDownLossAccountCode,
        shrinkageLossAccountCode,
        countGainAccountCode,
        equityAccountCode,
        amount,
        quantity,
        unitCost,
        inventoryRelief,
        settlementAdjunct,
        foreignExchange,
        tax,
        lines,
        openingBalances,
        evidence,
        provenance,
        reversal);
  }

  record ValidatedPostingRequestTemplateDescriptor(
      BookkeepingEntryKind entryKind,
      String effectiveDate,
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String writeDownLossAccountCode,
      @Nullable String shrinkageLossAccountCode,
      @Nullable String countGainAccountCode,
      @Nullable String equityAccountCode,
      @Nullable MonetaryAmount amount,
      @Nullable String quantity,
      @Nullable MonetaryAmount unitCost,
      @Nullable InventoryReliefTemplateDescriptor inventoryRelief,
      @Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct,
      @Nullable ForeignExchangeTemplateDescriptor foreignExchange,
      @Nullable TaxSelectionTemplateDescriptor tax,
      @Nullable List<JournalLineTemplateDescriptor> lines,
      @Nullable List<OpeningBalanceTemplateDescriptor> openingBalances,
      AccountingEvidenceTemplateDescriptor evidence,
      ProvenanceTemplateDescriptor provenance,
      @Nullable ReversalTemplateDescriptor reversal) {}

  record PostingRequestTemplateDraft(
      BookkeepingEntryKind entryKind,
      String effectiveDate,
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String writeDownLossAccountCode,
      @Nullable String shrinkageLossAccountCode,
      @Nullable String countGainAccountCode,
      @Nullable String equityAccountCode,
      @Nullable MonetaryAmount amount,
      @Nullable String quantity,
      @Nullable MonetaryAmount unitCost,
      @Nullable InventoryReliefTemplateDescriptor inventoryRelief,
      @Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct,
      @Nullable ForeignExchangeTemplateDescriptor foreignExchange,
      @Nullable TaxSelectionTemplateDescriptor tax,
      @Nullable List<JournalLineTemplateDescriptor> lines,
      @Nullable List<OpeningBalanceTemplateDescriptor> openingBalances,
      AccountingEvidenceTemplateDescriptor evidence,
      ProvenanceTemplateDescriptor provenance,
      @Nullable ReversalTemplateDescriptor reversal) {}
}
