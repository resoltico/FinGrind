package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Maps each post-entry kind to the context that owns its request-template scaffold. */
final class MachineContractPostEntryTemplateCatalog {
  private static final Map<BookkeepingEntryKind, TemplateBuilder> TEMPLATES = templateBuilders();

  private MachineContractPostEntryTemplateCatalog() {}

  private static Map<BookkeepingEntryKind, TemplateBuilder> templateBuilders() {
    var templates = new EnumMap<BookkeepingEntryKind, TemplateBuilder>(BookkeepingEntryKind.class);
    templates.putAll(baseTemplates());
    templates.putAll(standardTemplates());
    templates.putAll(inventoryTemplates());
    templates.putAll(accrualCutoffTemplates());
    templates.putAll(fixedAssetTemplates());
    templates.putAll(financingTemplates());
    templates.putAll(realizedForeignExchangeTemplates());
    templates.putAll(latvianPayrollTemplates());
    return Map.copyOf(templates);
  }

  private static Map<BookkeepingEntryKind, TemplateBuilder> baseTemplates() {
    return Map.of(
        BookkeepingEntryKind.DIRECT_JOURNAL,
        ignoredBookTemplateId -> MachineContractPostEntryVariantTemplates.directJournalTemplate(),
        BookkeepingEntryKind.OPENING_POSITION,
        ignoredBookTemplateId -> MachineContractPostEntryVariantTemplates.openingPositionTemplate(),
        BookkeepingEntryKind.REVERSAL,
        ignoredBookTemplateId -> MachineContractPostEntryVariantTemplates.reversalTemplate());
  }

  private static Map<BookkeepingEntryKind, TemplateBuilder> standardTemplates() {
    return Map.ofEntries(
        Map.entry(
            BookkeepingEntryKind.SALE_SETTLED,
            MachineContractPostEntryTypedVariantTemplates::saleSettledTemplate),
        Map.entry(
            BookkeepingEntryKind.SALE_ON_CREDIT,
            MachineContractPostEntryTypedVariantTemplates::saleOnCreditTemplate),
        Map.entry(
            BookkeepingEntryKind.EXPENSE_SETTLED,
            MachineContractPostEntryTypedVariantTemplates::expenseSettledTemplate),
        Map.entry(
            BookkeepingEntryKind.EXPENSE_ON_CREDIT,
            MachineContractPostEntryTypedVariantTemplates::expenseOnCreditTemplate),
        Map.entry(
            BookkeepingEntryKind.RECEIPT,
            MachineContractPostEntryTypedVariantTemplates::receiptTemplate),
        Map.entry(
            BookkeepingEntryKind.PAYMENT,
            MachineContractPostEntryTypedVariantTemplates::paymentTemplate),
        Map.entry(
            BookkeepingEntryKind.OWNER_CONTRIBUTION,
            MachineContractPostEntryTypedVariantTemplates::ownerContributionTemplate),
        Map.entry(
            BookkeepingEntryKind.OWNER_WITHDRAWAL,
            MachineContractPostEntryTypedVariantTemplates::ownerWithdrawalTemplate));
  }

  private static Map<BookkeepingEntryKind, TemplateBuilder> inventoryTemplates() {
    return Map.ofEntries(
        Map.entry(
            BookkeepingEntryKind.PURCHASE_SETTLED,
            MachineContractPostEntryTypedVariantTemplates::purchaseSettledTemplate),
        Map.entry(
            BookkeepingEntryKind.PURCHASE_ON_CREDIT,
            MachineContractPostEntryTypedVariantTemplates::purchaseOnCreditTemplate),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
            MachineContractPostEntryTypedVariantTemplates::inventoryCapitalizationSettledTemplate),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
            MachineContractPostEntryTypedVariantTemplates::inventoryCapitalizationOnCreditTemplate),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_WRITE_DOWN,
            MachineContractPostEntryTypedVariantTemplates::inventoryWriteDownTemplate),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_SHRINKAGE,
            MachineContractPostEntryTypedVariantTemplates::inventoryShrinkageTemplate),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_COUNT_INCREASE,
            MachineContractPostEntryTypedVariantTemplates::inventoryCountIncreaseTemplate));
  }

  private static Map<BookkeepingEntryKind, TemplateBuilder> accrualCutoffTemplates() {
    return Map.of(
        BookkeepingEntryKind.PREPAYMENT,
        MachineContractAccrualCutoffPostEntryVariantTemplates::prepaymentTemplate,
        BookkeepingEntryKind.DEFERRED_REVENUE,
        MachineContractAccrualCutoffPostEntryVariantTemplates::deferredRevenueTemplate,
        BookkeepingEntryKind.ACCRUED_EXPENSE,
        MachineContractAccrualCutoffPostEntryVariantTemplates::accruedExpenseTemplate,
        BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION,
        MachineContractAccrualCutoffPostEntryVariantTemplates::accrualCutoffRecognitionTemplate,
        BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT,
        MachineContractAccrualCutoffPostEntryVariantTemplates::accruedExpenseSettlementTemplate);
  }

  private static Map<BookkeepingEntryKind, TemplateBuilder> fixedAssetTemplates() {
    return Map.of(
        BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION,
        MachineContractFixedAssetPostEntryVariantTemplates::capitalizationTemplate,
        BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION,
        MachineContractFixedAssetPostEntryVariantTemplates::depreciationTemplate,
        BookkeepingEntryKind.FIXED_ASSET_DISPOSAL,
        MachineContractFixedAssetPostEntryVariantTemplates::disposalTemplate);
  }

  private static Map<BookkeepingEntryKind, TemplateBuilder> financingTemplates() {
    return Map.of(
        BookkeepingEntryKind.FINANCING_BORROWING,
        MachineContractFinancingPostEntryVariantTemplates::borrowingTemplate,
        BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT,
        MachineContractFinancingPostEntryVariantTemplates::principalRepaymentTemplate,
        BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL,
        MachineContractFinancingPostEntryVariantTemplates::interestAccrualTemplate,
        BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT,
        MachineContractFinancingPostEntryVariantTemplates::interestPaymentTemplate);
  }

  private static Map<BookkeepingEntryKind, TemplateBuilder> realizedForeignExchangeTemplates() {
    return Map.of(
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
        MachineContractRealizedForeignExchangePostEntryVariantTemplates
            ::foreignCurrencyObligationTemplate,
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        MachineContractRealizedForeignExchangePostEntryVariantTemplates::settlementTemplate);
  }

  private static Map<BookkeepingEntryKind, TemplateBuilder> latvianPayrollTemplates() {
    return Map.of(
        BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL,
        MachineContractLatvianPayrollPostEntryVariantTemplates::monthlyPayrollTemplate,
        BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
        MachineContractLatvianPayrollPostEntryVariantTemplates::netWageSettlementTemplate,
        BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE,
        MachineContractLatvianPayrollPostEntryVariantTemplates::stateRemittanceTemplate);
  }

  static ContractTemplates.PostingRequestTemplateDescriptor template(
      BookkeepingEntryKind entryKind, @Nullable BookTemplateId bookTemplateId) {
    ContractTemplates.PostingRequestTemplateDescriptor template =
        Objects.requireNonNull(TEMPLATES.get(entryKind), "entryKind").build(bookTemplateId);
    return MachineContractPostEntryTaxTemplateSupport.withOptionalTaxSelection(template);
  }

  /** Context-specific builder for one published post-entry request template. */
  @FunctionalInterface
  private interface TemplateBuilder {
    /** Builds the template for the supplied optional book doctrine. */
    ContractTemplates.PostingRequestTemplateDescriptor build(
        @Nullable BookTemplateId bookTemplateId);
  }
}
