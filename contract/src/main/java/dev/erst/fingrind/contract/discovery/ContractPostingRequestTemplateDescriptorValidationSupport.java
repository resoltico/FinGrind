package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractFinancingTemplates.FinancingTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractFixedAssetTemplates.FixedAssetTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractLatvianPayrollTemplates.MonthlyPayrollTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractLatvianPayrollTemplates.PayrollSettlementTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRealizedForeignExchangeTemplates.RealizedForeignExchangeTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractSettlementTemplates.SettlementAdjunctTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractSettlementTemplates.TaxSelectionTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.AccountingEvidenceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.ProvenanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.RecognitionIntervalTemplateDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Validation owner for posting request-template descriptors. */
final class ContractPostingRequestTemplateDescriptorValidationSupport {
  private ContractPostingRequestTemplateDescriptorValidationSupport() {}

  static ValidatedPostingRequestTemplateDescriptor validate(PostingRequestTemplateDraft draft) {
    ValidatedTemplateDraft validatedDraft = validateDraft(draft);
    ContractPostingRequestTemplateValidators.validate(
        validatedDraft.entryKind(), validatedDraft.fields(), validatedDraft.reversal());
    return validatedDraft.descriptor();
  }

  private static ValidatedTemplateDraft validateDraft(PostingRequestTemplateDraft draft) {
    return new ValidatedTemplateDraft(
        ContractDescriptorValidation.requireValue(draft.entryKind(), "entryKind"),
        ContractDescriptorValidation.requireText(draft.effectiveDate(), "effectiveDate"),
        new ContractPostingRequestTemplateValidators.PostingTemplateFields(
            ContractDescriptorValidation.requireOptionalText(
                draft.cashAccountCode(), "cashAccountCode"),
            ContractDescriptorValidation.requireOptionalText(
                draft.receivableAccountCode(), "receivableAccountCode"),
            ContractDescriptorValidation.requireOptionalText(
                draft.payableAccountCode(), "payableAccountCode"),
            ContractDescriptorValidation.requireOptionalText(
                draft.revenueAccountCode(), "revenueAccountCode"),
            ContractDescriptorValidation.requireOptionalText(
                draft.inventoryAccountCode(), "inventoryAccountCode"),
            ContractDescriptorValidation.requireOptionalText(
                draft.expenseAccountCode(), "expenseAccountCode"),
            ContractDescriptorValidation.requireOptionalText(
                draft.writeDownLossAccountCode(), "writeDownLossAccountCode"),
            ContractDescriptorValidation.requireOptionalText(
                draft.shrinkageLossAccountCode(), "shrinkageLossAccountCode"),
            ContractDescriptorValidation.requireOptionalText(
                draft.countGainAccountCode(), "countGainAccountCode"),
            ContractDescriptorValidation.requireOptionalText(
                draft.equityAccountCode(), "equityAccountCode"),
            ContractDescriptorValidation.requireOptionalValue(draft.amount(), "amount"),
            ContractDescriptorValidation.requireOptionalText(draft.quantity(), "quantity"),
            ContractDescriptorValidation.requireOptionalValue(draft.unitCost(), "unitCost"),
            ContractDescriptorValidation.requireOptionalValue(
                draft.inventoryRelief(), "inventoryRelief"),
            ContractDescriptorValidation.requireOptionalValue(
                draft.settlementAdjunct(), "settlementAdjunct"),
            ContractDescriptorValidation.requireOptionalValue(
                draft.foreignExchange(), "foreignExchange"),
            ContractDescriptorValidation.requireOptionalValue(draft.tax(), "tax"),
            copyOptionalList(draft.lines(), "lines"),
            copyOptionalList(draft.openingBalances(), "openingBalances"),
            ContractDescriptorValidation.requireOptionalText(
                draft.accrualCutoffId(), "accrualCutoffId"),
            ContractDescriptorValidation.requireOptionalText(
                draft.prepaymentAssetAccountCode(), "prepaymentAssetAccountCode"),
            ContractDescriptorValidation.requireOptionalText(
                draft.deferredRevenueAccountCode(), "deferredRevenueAccountCode"),
            ContractDescriptorValidation.requireOptionalText(
                draft.accruedExpenseLiabilityAccountCode(), "accruedExpenseLiabilityAccountCode"),
            ContractDescriptorValidation.requireOptionalValue(
                draft.recognitionInterval(), "recognitionInterval"),
            ContractDescriptorValidation.requireOptionalValue(
                draft.latvianMonthlyPayroll(), "latvianMonthlyPayroll"),
            ContractDescriptorValidation.requireOptionalValue(
                draft.latvianPayrollSettlement(), "latvianPayrollSettlement"),
            ContractDescriptorValidation.requireOptionalValue(draft.fixedAsset(), "fixedAsset"),
            ContractDescriptorValidation.requireOptionalValue(draft.financing(), "financing"),
            ContractDescriptorValidation.requireOptionalValue(
                draft.realizedForeignExchange(), "realizedForeignExchange")),
        ContractDescriptorValidation.requireValue(draft.evidence(), "evidence"),
        ContractDescriptorValidation.requireValue(draft.provenance(), "provenance"),
        draft.reversal());
  }

  private static <T> @Nullable List<T> copyOptionalList(@Nullable List<T> values, String name) {
    return values == null ? null : ContractDescriptorValidation.copyList(values, name);
  }

  private record ValidatedTemplateDraft(
      BookkeepingEntryKind entryKind,
      String effectiveDate,
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      AccountingEvidenceTemplateDescriptor evidence,
      ProvenanceTemplateDescriptor provenance,
      @Nullable ReversalTemplateDescriptor reversal) {
    private ValidatedPostingRequestTemplateDescriptor descriptor() {
      return new ValidatedPostingRequestTemplateDescriptor(
          entryKind,
          effectiveDate,
          fields.cashAccountCode(),
          fields.receivableAccountCode(),
          fields.payableAccountCode(),
          fields.revenueAccountCode(),
          fields.inventoryAccountCode(),
          fields.expenseAccountCode(),
          fields.writeDownLossAccountCode(),
          fields.shrinkageLossAccountCode(),
          fields.countGainAccountCode(),
          fields.equityAccountCode(),
          fields.amount(),
          fields.quantity(),
          fields.unitCost(),
          fields.inventoryRelief(),
          fields.settlementAdjunct(),
          fields.foreignExchange(),
          fields.tax(),
          fields.lines(),
          fields.openingBalances(),
          evidence,
          provenance,
          reversal,
          fields.accrualCutoffId(),
          fields.prepaymentAssetAccountCode(),
          fields.deferredRevenueAccountCode(),
          fields.accruedExpenseLiabilityAccountCode(),
          fields.recognitionInterval(),
          fields.latvianMonthlyPayroll(),
          fields.latvianPayrollSettlement(),
          fields.fixedAsset(),
          fields.financing(),
          fields.realizedForeignExchange());
    }
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
      @Nullable ReversalTemplateDescriptor reversal,
      @Nullable String accrualCutoffId,
      @Nullable String prepaymentAssetAccountCode,
      @Nullable String deferredRevenueAccountCode,
      @Nullable String accruedExpenseLiabilityAccountCode,
      @Nullable RecognitionIntervalTemplateDescriptor recognitionInterval,
      @Nullable MonthlyPayrollTemplateDescriptor latvianMonthlyPayroll,
      @Nullable PayrollSettlementTemplateDescriptor latvianPayrollSettlement,
      @Nullable FixedAssetTemplateDescriptor fixedAsset,
      @Nullable FinancingTemplateDescriptor financing,
      @Nullable RealizedForeignExchangeTemplateDescriptor realizedForeignExchange) {}

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
      @Nullable ReversalTemplateDescriptor reversal,
      @Nullable String accrualCutoffId,
      @Nullable String prepaymentAssetAccountCode,
      @Nullable String deferredRevenueAccountCode,
      @Nullable String accruedExpenseLiabilityAccountCode,
      @Nullable RecognitionIntervalTemplateDescriptor recognitionInterval,
      @Nullable MonthlyPayrollTemplateDescriptor latvianMonthlyPayroll,
      @Nullable PayrollSettlementTemplateDescriptor latvianPayrollSettlement,
      @Nullable FixedAssetTemplateDescriptor fixedAsset,
      @Nullable FinancingTemplateDescriptor financing,
      @Nullable RealizedForeignExchangeTemplateDescriptor realizedForeignExchange) {}
}
