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
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Posting-template validator owner for the discovery contract namespace. */
final class ContractPostingRequestTemplateValidators {
  private static final Map<BookkeepingEntryKind, PostingTemplateValidator> VALIDATORS =
      validators();

  private ContractPostingRequestTemplateValidators() {}

  static void validate(
      BookkeepingEntryKind entryKind,
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal) {
    Objects.requireNonNull(entryKind, "entryKind");
    validateAccrualAndPayrollFieldEligibility(entryKind, fields);
    validateLifecycleFieldEligibility(entryKind, fields);
    Objects.requireNonNull(VALIDATORS.get(entryKind), "entryKind").validate(fields, reversal);
  }

  private static void validateAccrualAndPayrollFieldEligibility(
      BookkeepingEntryKind entryKind, PostingTemplateFields fields) {
    if (!isAccrualCutoffEntryKind(entryKind)) {
      ContractAccrualCutoffPostingRequestTemplateValidators.requireNoAccrualCutoffFields(
          fields, entryKind.wireValue());
    }
    if (entryKind != BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL
        && fields.latvianMonthlyPayroll() != null) {
      throw new IllegalArgumentException(
          "latvianMonthlyPayroll must be absent for " + entryKind.wireValue() + ".");
    }
    if (entryKind != BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT
        && entryKind != BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE
        && fields.latvianPayrollSettlement() != null) {
      throw new IllegalArgumentException(
          "latvianPayrollSettlement must be absent for " + entryKind.wireValue() + ".");
    }
  }

  private static void validateLifecycleFieldEligibility(
      BookkeepingEntryKind entryKind, PostingTemplateFields fields) {
    if (!isFixedAssetEntryKind(entryKind) && fields.fixedAsset() != null) {
      throw new IllegalArgumentException(
          "fixedAsset must be absent for " + entryKind.wireValue() + ".");
    }
    if (!isFinancingEntryKind(entryKind) && fields.financing() != null) {
      throw new IllegalArgumentException(
          "financing must be absent for " + entryKind.wireValue() + ".");
    }
    if (!isRealizedForeignExchangeEntryKind(entryKind)
        && fields.realizedForeignExchange() != null) {
      throw new IllegalArgumentException(
          "realizedForeignExchange must be absent for " + entryKind.wireValue() + ".");
    }
  }

  static void validateRoleAmountTemplate(
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal,
      RoleAmountTemplateValidationRule rule) {
    ContractPostingRequestTemplateFieldSupport.requireTextFields(fields, rule.requiredFields());
    ContractPostingTemplateScalarFieldRules.requirePositiveAmount(fields.amount());
    ContractPostingTemplateScalarFieldRules.forbidQuantity(fields.quantity(), rule.owner());
    ContractPostingTemplateScalarFieldRules.forbidUnitCost(fields.unitCost(), rule.owner());
    ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
        fields.inventoryRelief(), rule.owner(), rule.inventoryReliefPolicy());
    ContractPostingRequestTemplateFieldSupport.forbidTextFields(fields, rule.forbiddenFields());
    ContractPostingRequestTemplateFieldSupport.forbidInventoryMaintenanceFields(
        fields, rule.owner());
    if (!rule.settlementAdjunctAllowed()) {
      ContractPostingRequestTemplateFieldSupport.requireNoSettlementAdjunct(fields, rule.owner());
    }
    ContractPostingRequestTemplateFieldSupport.forbidLinesAndOpeningBalances(fields);
    if (!rule.taxAllowed()) {
      ContractPostingTemplateFieldRules.forbidTax(fields.tax(), rule.owner());
    }
    if (!rule.foreignExchangeAllowed()) {
      ContractPostingTemplateFieldRules.forbidForeignExchange(
          fields.foreignExchange(), rule.owner());
    }
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  static void validatePurchaseTemplate(
      PostingTemplateFields fields,
      @Nullable ReversalTemplateDescriptor reversal,
      RoleQuantityUnitCostTemplateValidationRule rule) {
    ContractPostingRequestTemplateFieldSupport.requireTextFields(fields, rule.requiredFields());
    ContractPostingRequestTemplateFieldSupport.forbidTextFields(fields, rule.forbiddenFields());
    ContractPostingRequestTemplateFieldSupport.forbidInventoryMaintenanceFields(
        fields, rule.owner());
    ContractPostingTemplateScalarFieldRules.forbidAmount(fields.amount(), rule.owner());
    ContractPostingTemplateScalarFieldRules.requirePositiveQuantity(fields.quantity());
    ContractPostingTemplateScalarFieldRules.requirePositiveUnitCost(fields.unitCost());
    ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
        fields.inventoryRelief(),
        rule.owner(),
        ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN);
    ContractPostingRequestTemplateFieldSupport.requireNoSettlementAdjunct(fields, rule.owner());
    ContractPostingRequestTemplateFieldSupport.forbidLinesAndOpeningBalances(fields);
    if (!rule.taxAllowed()) {
      ContractPostingTemplateFieldRules.forbidTax(fields.tax(), rule.owner());
    }
    if (!rule.foreignExchangeAllowed()) {
      ContractPostingTemplateFieldRules.forbidForeignExchange(
          fields.foreignExchange(), rule.owner());
    }
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  static PostingTemplateValidator roleAmountValidator(RoleAmountTemplateValidationRule rule) {
    return (fields, reversal) -> validateRoleAmountTemplate(fields, reversal, rule);
  }

  static PostingTemplateValidator purchaseValidator(
      RoleQuantityUnitCostTemplateValidationRule rule) {
    return (fields, reversal) -> validatePurchaseTemplate(fields, reversal, rule);
  }

  record PostingTemplateFields(
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
      @Nullable String accrualCutoffId,
      @Nullable String prepaymentAssetAccountCode,
      @Nullable String deferredRevenueAccountCode,
      @Nullable String accruedExpenseLiabilityAccountCode,
      ContractTemplates.@Nullable RecognitionIntervalTemplateDescriptor recognitionInterval,
      @Nullable MonthlyPayrollTemplateDescriptor latvianMonthlyPayroll,
      @Nullable PayrollSettlementTemplateDescriptor latvianPayrollSettlement,
      @Nullable FixedAssetTemplateDescriptor fixedAsset,
      @Nullable FinancingTemplateDescriptor financing,
      @Nullable RealizedForeignExchangeTemplateDescriptor realizedForeignExchange) {}

  /** Variant-specific validation contract for one posting-template kind. */
  @FunctionalInterface
  interface PostingTemplateValidator {
    /** Validates one posting-template field bundle for one entry kind. */
    void validate(PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal);
  }

  record RoleAmountTemplateValidationRule(
      String owner,
      ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy inventoryReliefPolicy,
      boolean settlementAdjunctAllowed,
      boolean foreignExchangeAllowed,
      boolean taxAllowed,
      List<ContractPostingRequestTemplateFieldSupport.TemplateTextField> requiredFields,
      List<ContractPostingRequestTemplateFieldSupport.TemplateTextField> forbiddenFields) {}

  record RoleQuantityUnitCostTemplateValidationRule(
      String owner,
      boolean foreignExchangeAllowed,
      boolean taxAllowed,
      List<ContractPostingRequestTemplateFieldSupport.TemplateTextField> requiredFields,
      List<ContractPostingRequestTemplateFieldSupport.TemplateTextField> forbiddenFields) {}

  private static Map<BookkeepingEntryKind, PostingTemplateValidator> validators() {
    var validators =
        new java.util.EnumMap<BookkeepingEntryKind, PostingTemplateValidator>(
            BookkeepingEntryKind.class);
    validators.put(
        BookkeepingEntryKind.DIRECT_JOURNAL,
        ContractPostingRequestTemplateSpecialCaseValidators::validateDirectJournalTemplate);
    validators.putAll(ContractStandardPostingRequestTemplateValidators.validators());
    validators.putAll(ContractInventoryPostingRequestTemplateValidators.validators());
    validators.putAll(ContractAccrualCutoffPostingRequestTemplateValidators.validators());
    validators.put(
        BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION,
        ContractFixedAssetPostingRequestTemplateValidators::validateCapitalizationTemplate);
    validators.put(
        BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION,
        ContractFixedAssetPostingRequestTemplateValidators::validateDepreciationTemplate);
    validators.put(
        BookkeepingEntryKind.FIXED_ASSET_DISPOSAL,
        ContractFixedAssetPostingRequestTemplateValidators::validateDisposalTemplate);
    validators.putAll(ContractFinancingPostingRequestTemplateValidators.validators());
    validators.putAll(ContractRealizedForeignExchangePostingRequestTemplateValidators.validators());
    validators.put(
        BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL,
        ContractLatvianPayrollPostingRequestTemplateValidators::validateMonthlyPayrollTemplate);
    validators.put(
        BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
        ContractLatvianPayrollPostingRequestTemplateValidators::validateSettlementTemplate);
    validators.put(
        BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE,
        ContractLatvianPayrollPostingRequestTemplateValidators::validateSettlementTemplate);
    validators.put(
        BookkeepingEntryKind.OPENING_POSITION,
        ContractPostingRequestTemplateSpecialCaseValidators::validateOpeningPositionTemplate);
    validators.put(
        BookkeepingEntryKind.REVERSAL,
        ContractPostingRequestTemplateSpecialCaseValidators::validateReversalTemplate);
    return Map.copyOf(validators);
  }

  private static boolean isAccrualCutoffEntryKind(BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case PREPAYMENT,
          DEFERRED_REVENUE,
          ACCRUED_EXPENSE,
          ACCRUAL_CUTOFF_RECOGNITION,
          ACCRUED_EXPENSE_SETTLEMENT ->
          true;
      default -> false;
    };
  }

  private static boolean isFixedAssetEntryKind(BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case FIXED_ASSET_CAPITALIZATION, FIXED_ASSET_DEPRECIATION, FIXED_ASSET_DISPOSAL -> true;
      default -> false;
    };
  }

  private static boolean isFinancingEntryKind(BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case FINANCING_BORROWING,
          FINANCING_PRINCIPAL_REPAYMENT,
          FINANCING_INTEREST_ACCRUAL,
          FINANCING_INTEREST_PAYMENT ->
          true;
      default -> false;
    };
  }

  private static boolean isRealizedForeignExchangeEntryKind(BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case FOREIGN_CURRENCY_OBLIGATION, REALIZED_FOREIGN_EXCHANGE_SETTLEMENT -> true;
      default -> false;
    };
  }
}
