package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateValidators.PostingTemplateFields;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateValidators.PostingTemplateValidator;
import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Validation rules for request templates owned by the accrual cut-off context. */
final class ContractAccrualCutoffPostingRequestTemplateValidators {
  private ContractAccrualCutoffPostingRequestTemplateValidators() {}

  static Map<BookkeepingEntryKind, PostingTemplateValidator> validators() {
    return Map.of(
        BookkeepingEntryKind.PREPAYMENT,
        (fields, reversal) -> validatePrepayment(fields, reversal),
        BookkeepingEntryKind.DEFERRED_REVENUE,
        (fields, reversal) -> validateDeferredRevenue(fields, reversal),
        BookkeepingEntryKind.ACCRUED_EXPENSE,
        (fields, reversal) -> validateAccruedExpense(fields, reversal),
        BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION,
        (fields, reversal) -> validateRecognition(fields, reversal),
        BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT,
        (fields, reversal) -> validateSettlement(fields, reversal));
  }

  static void requireNoAccrualCutoffFields(PostingTemplateFields fields, String owner) {
    ContractPostingTemplateFieldRules.forbidText(fields.accrualCutoffId(), "accrualCutoffId");
    ContractPostingTemplateFieldRules.forbidText(
        fields.prepaymentAssetAccountCode(), "prepaymentAssetAccountCode");
    ContractPostingTemplateFieldRules.forbidText(
        fields.deferredRevenueAccountCode(), "deferredRevenueAccountCode");
    ContractPostingTemplateFieldRules.forbidText(
        fields.accruedExpenseLiabilityAccountCode(), "accruedExpenseLiabilityAccountCode");
    if (fields.recognitionInterval() != null) {
      throw new IllegalArgumentException("recognitionInterval must be absent for " + owner + ".");
    }
  }

  private static void validatePrepayment(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    requireCommon(fields, reversal, "prepayment");
    require(fields.cashAccountCode(), "cashAccountCode");
    require(fields.expenseAccountCode(), "expenseAccountCode");
    require(fields.prepaymentAssetAccountCode(), "prepaymentAssetAccountCode");
    requireRecognitionInterval(fields, "prepayment");
    forbid(
        "prepayment",
        fields.receivableAccountCode(),
        fields.payableAccountCode(),
        fields.revenueAccountCode(),
        fields.inventoryAccountCode(),
        fields.writeDownLossAccountCode(),
        fields.shrinkageLossAccountCode(),
        fields.countGainAccountCode(),
        fields.equityAccountCode(),
        fields.deferredRevenueAccountCode(),
        fields.accruedExpenseLiabilityAccountCode());
  }

  private static void validateDeferredRevenue(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    requireCommon(fields, reversal, "deferredRevenue");
    require(fields.cashAccountCode(), "cashAccountCode");
    require(fields.revenueAccountCode(), "revenueAccountCode");
    require(fields.deferredRevenueAccountCode(), "deferredRevenueAccountCode");
    requireRecognitionInterval(fields, "deferredRevenue");
    forbid(
        "deferredRevenue",
        fields.receivableAccountCode(),
        fields.payableAccountCode(),
        fields.inventoryAccountCode(),
        fields.expenseAccountCode(),
        fields.writeDownLossAccountCode(),
        fields.shrinkageLossAccountCode(),
        fields.countGainAccountCode(),
        fields.equityAccountCode(),
        fields.prepaymentAssetAccountCode(),
        fields.accruedExpenseLiabilityAccountCode());
  }

  private static void validateAccruedExpense(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    requireCommon(fields, reversal, "accruedExpense");
    require(fields.expenseAccountCode(), "expenseAccountCode");
    require(fields.accruedExpenseLiabilityAccountCode(), "accruedExpenseLiabilityAccountCode");
    forbidRecognitionInterval(fields, "accruedExpense");
    forbid(
        "accruedExpense",
        fields.cashAccountCode(),
        fields.receivableAccountCode(),
        fields.payableAccountCode(),
        fields.revenueAccountCode(),
        fields.inventoryAccountCode(),
        fields.writeDownLossAccountCode(),
        fields.shrinkageLossAccountCode(),
        fields.countGainAccountCode(),
        fields.equityAccountCode(),
        fields.prepaymentAssetAccountCode(),
        fields.deferredRevenueAccountCode());
  }

  private static void validateRecognition(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    requireCommon(fields, reversal, "accrualCutoffRecognition");
    forbidRecognitionInterval(fields, "accrualCutoffRecognition");
    forbid(
        "accrualCutoffRecognition",
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
        fields.prepaymentAssetAccountCode(),
        fields.deferredRevenueAccountCode(),
        fields.accruedExpenseLiabilityAccountCode());
  }

  private static void validateSettlement(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    requireCommon(fields, reversal, "accruedExpenseSettlement");
    require(fields.cashAccountCode(), "cashAccountCode");
    forbidRecognitionInterval(fields, "accruedExpenseSettlement");
    forbid(
        "accruedExpenseSettlement",
        fields.receivableAccountCode(),
        fields.payableAccountCode(),
        fields.revenueAccountCode(),
        fields.inventoryAccountCode(),
        fields.expenseAccountCode(),
        fields.writeDownLossAccountCode(),
        fields.shrinkageLossAccountCode(),
        fields.countGainAccountCode(),
        fields.equityAccountCode(),
        fields.prepaymentAssetAccountCode(),
        fields.deferredRevenueAccountCode(),
        fields.accruedExpenseLiabilityAccountCode());
  }

  private static void requireCommon(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal, String owner) {
    require(fields.accrualCutoffId(), "accrualCutoffId");
    ContractPostingTemplateScalarFieldRules.requirePositiveAmount(fields.amount());
    ContractPostingTemplateScalarFieldRules.forbidQuantity(fields.quantity(), owner);
    ContractPostingTemplateScalarFieldRules.forbidUnitCost(fields.unitCost(), owner);
    ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
        fields.inventoryRelief(),
        owner,
        ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN);
    ContractPostingRequestTemplateFieldSupport.requireNoSettlementAdjunct(fields, owner);
    ContractPostingRequestTemplateFieldSupport.forbidLinesAndOpeningBalances(fields);
    ContractPostingTemplateFieldRules.forbidForeignExchange(fields.foreignExchange(), owner);
    ContractPostingTemplateFieldRules.forbidTax(fields.tax(), owner);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void requireRecognitionInterval(PostingTemplateFields fields, String owner) {
    if (fields.recognitionInterval() == null) {
      throw new IllegalArgumentException("recognitionInterval is required for " + owner + ".");
    }
  }

  private static void forbidRecognitionInterval(PostingTemplateFields fields, String owner) {
    if (fields.recognitionInterval() != null) {
      throw new IllegalArgumentException("recognitionInterval must be absent for " + owner + ".");
    }
  }

  private static void require(@Nullable String value, String fieldName) {
    ContractPostingTemplateFieldRules.requireText(value, fieldName);
  }

  private static void forbid(String owner, @Nullable String... values) {
    for (String value : values) {
      ContractPostingTemplateFieldRules.forbidText(value, owner + " account field");
    }
  }
}
