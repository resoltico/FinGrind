package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Owns canonical machine scaffolds for atomic ledger plans. */
final class MachineContractPlanTemplates {
  private MachineContractPlanTemplates() {}

  static ContractPlanTemplates.LedgerPlanTemplateDescriptor template(PlanTemplateTopic topic) {
    return switch (java.util.Objects.requireNonNull(topic, "topic")) {
      case GENERAL -> general();
      case TAX_SETUP -> taxSetup();
      case FIXED_ASSET_SETUP -> fixedAssetSetup();
      case FINANCING_SETUP -> financingSetup();
    };
  }

  private static ContractPlanTemplates.LedgerPlanTemplateDescriptor general() {
    return new ContractPlanTemplates.LedgerPlanTemplateDescriptor(
        "general-workflow",
        List.of(
            new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
                ProtocolCatalog.operationName(OperationId.RECORD_SALE_SETTLED),
                LedgerStepKind.RECORD_SALE_SETTLED,
                null,
                MachineContractPostEntryVariantSchemas.template(BookkeepingEntryKind.SALE_SETTLED),
                null,
                null,
                null,
                null,
                null)));
  }

  private static ContractPlanTemplates.LedgerPlanTemplateDescriptor taxSetup() {
    return new ContractPlanTemplates.LedgerPlanTemplateDescriptor(
        "tax-setup",
        List.of(
            declareAccount(
                "declare-tax-payable",
                "tax-payable-vat",
                "VAT Payable",
                AccountType.LIABILITY,
                FinancialPositionLineClassification.CURRENT_LIABILITY,
                null,
                null,
                null),
            declareAccount(
                "declare-tax-recoverable",
                "tax-recoverable-vat",
                "VAT Recoverable",
                AccountType.ASSET,
                FinancialPositionLineClassification.CURRENT_ASSET,
                null,
                CashFlowAssetClassification.NON_CASH,
                null),
            new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
                ProtocolCatalog.operationName(OperationId.DECLARE_TAX_REGISTRATION),
                LedgerStepKind.DECLARE_TAX_REGISTRATION,
                null,
                null,
                null,
                MachineContractTemplatesCatalog.declareTaxRegistrationTemplate(),
                null,
                null,
                null)));
  }

  private static ContractPlanTemplates.LedgerPlanTemplateDescriptor fixedAssetSetup() {
    return new ContractPlanTemplates.LedgerPlanTemplateDescriptor(
        "fixed-asset-setup",
        List.of(
            declareAccount(
                "declare-delivery-van",
                "delivery-van",
                "Delivery Van",
                AccountType.ASSET,
                FinancialPositionLineClassification.NONCURRENT_ASSET,
                null,
                CashFlowAssetClassification.NON_CASH,
                null),
            declareAccount(
                "declare-delivery-van-accumulated-depreciation",
                "delivery-van-accumulated-depreciation",
                "Delivery Van Accumulated Depreciation",
                AccountType.ASSET,
                FinancialPositionLineClassification.NONCURRENT_ASSET,
                null,
                CashFlowAssetClassification.NON_CASH,
                "delivery-van"),
            declareAccount(
                "declare-depreciation-expense",
                "depreciation-expense",
                "Depreciation Expense",
                AccountType.EXPENSE,
                null,
                ProfitAndLossLineClassification.DEPRECIATION_AND_AMORTIZATION,
                null,
                null),
            declareAccount(
                "declare-fixed-asset-disposal-gain",
                "fixed-asset-disposal-gain",
                "Fixed Asset Disposal Gain",
                AccountType.REVENUE,
                null,
                ProfitAndLossLineClassification.OTHER_REVENUE,
                null,
                null),
            declareAccount(
                "declare-fixed-asset-disposal-loss",
                "fixed-asset-disposal-loss",
                "Fixed Asset Disposal Loss",
                AccountType.EXPENSE,
                null,
                ProfitAndLossLineClassification.OTHER_EXPENSE,
                null,
                null)));
  }

  private static ContractPlanTemplates.LedgerPlanTemplateDescriptor financingSetup() {
    return new ContractPlanTemplates.LedgerPlanTemplateDescriptor(
        "financing-setup",
        List.of(
            declareAccount(
                "declare-term-loan-principal",
                "term-loan-principal",
                "Term Loan Principal",
                AccountType.LIABILITY,
                FinancialPositionLineClassification.NONCURRENT_LIABILITY,
                null,
                null,
                null),
            declareAccount(
                "declare-term-loan-interest-payable",
                "term-loan-interest-payable",
                "Term Loan Interest Payable",
                AccountType.LIABILITY,
                FinancialPositionLineClassification.CURRENT_LIABILITY,
                null,
                null,
                null),
            declareAccount(
                "declare-interest-expense",
                "interest-expense",
                "Interest Expense",
                AccountType.EXPENSE,
                null,
                ProfitAndLossLineClassification.FINANCE_EXPENSE,
                null,
                null)));
  }

  private static ContractPlanTemplates.LedgerPlanStepTemplateDescriptor declareAccount(
      String stepId,
      String accountCode,
      String accountName,
      AccountType accountType,
      @Nullable FinancialPositionLineClassification financialPositionLineClassification,
      @Nullable ProfitAndLossLineClassification profitAndLossLineClassification,
      @Nullable CashFlowAssetClassification cashFlowAssetClassification,
      @Nullable String contraOfAccountCode) {
    return new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
        stepId,
        LedgerStepKind.DECLARE_ACCOUNT,
        null,
        null,
        new ContractTemplates.DeclareAccountTemplateDescriptor(
            accountCode,
            accountName,
            accountType,
            AccountNodeKind.POSTABLE,
            null,
            contraOfAccountCode,
            financialPositionLineClassification,
            profitAndLossLineClassification,
            cashFlowAssetClassification,
            null),
        null,
        null,
        null,
        null);
  }
}
