package dev.erst.fingrind.contract.discovery;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractFinancingTemplates.FinancingTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractFixedAssetTemplates.FixedAssetTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractLatvianPayrollTemplates.MonthlyPayrollTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractLatvianPayrollTemplates.PayrollSettlementTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRealizedForeignExchangeTemplates.RealizedForeignExchangeTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.AccountingEvidenceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.ProvenanceTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.RecognitionIntervalTemplateDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Request-template descriptors that describe caller-authored posting facts. */
public interface ContractPostingRequestTemplates
    extends ContractReversalTemplates, ContractSettlementTemplates {
  record PostingRequestTemplateDescriptor(
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
      @JsonUnwrapped @Nullable MonthlyPayrollTemplateDescriptor latvianMonthlyPayroll,
      @JsonUnwrapped @Nullable PayrollSettlementTemplateDescriptor latvianPayrollSettlement,
      @JsonUnwrapped @Nullable FixedAssetTemplateDescriptor fixedAsset,
      @JsonUnwrapped @Nullable FinancingTemplateDescriptor financing,
      @JsonUnwrapped @Nullable RealizedForeignExchangeTemplateDescriptor realizedForeignExchange)
      implements TemplateDescriptorType {
    public PostingRequestTemplateDescriptor {
      var validated =
          ContractPostingRequestTemplateDescriptorValidationSupport.validate(
              new ContractPostingRequestTemplateDescriptorValidationSupport
                  .PostingRequestTemplateDraft(
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
                  reversal,
                  accrualCutoffId,
                  prepaymentAssetAccountCode,
                  deferredRevenueAccountCode,
                  accruedExpenseLiabilityAccountCode,
                  recognitionInterval,
                  latvianMonthlyPayroll,
                  latvianPayrollSettlement,
                  fixedAsset,
                  financing,
                  realizedForeignExchange));
      entryKind = validated.entryKind();
      effectiveDate = validated.effectiveDate();
      cashAccountCode = validated.cashAccountCode();
      receivableAccountCode = validated.receivableAccountCode();
      payableAccountCode = validated.payableAccountCode();
      revenueAccountCode = validated.revenueAccountCode();
      inventoryAccountCode = validated.inventoryAccountCode();
      expenseAccountCode = validated.expenseAccountCode();
      writeDownLossAccountCode = validated.writeDownLossAccountCode();
      shrinkageLossAccountCode = validated.shrinkageLossAccountCode();
      countGainAccountCode = validated.countGainAccountCode();
      equityAccountCode = validated.equityAccountCode();
      amount = validated.amount();
      quantity = validated.quantity();
      unitCost = validated.unitCost();
      inventoryRelief = validated.inventoryRelief();
      settlementAdjunct = validated.settlementAdjunct();
      foreignExchange = validated.foreignExchange();
      tax = validated.tax();
      lines = validated.lines();
      openingBalances = validated.openingBalances();
      evidence = validated.evidence();
      provenance = validated.provenance();
      reversal = validated.reversal();
      accrualCutoffId = validated.accrualCutoffId();
      prepaymentAssetAccountCode = validated.prepaymentAssetAccountCode();
      deferredRevenueAccountCode = validated.deferredRevenueAccountCode();
      accruedExpenseLiabilityAccountCode = validated.accruedExpenseLiabilityAccountCode();
      recognitionInterval = validated.recognitionInterval();
      latvianMonthlyPayroll = validated.latvianMonthlyPayroll();
      latvianPayrollSettlement = validated.latvianPayrollSettlement();
      fixedAsset = validated.fixedAsset();
      financing = validated.financing();
      realizedForeignExchange = validated.realizedForeignExchange();
    }
  }
}
