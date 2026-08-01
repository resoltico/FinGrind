package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical request-field sets for typed accrual cut-off entries. */
public final class ProtocolAccrualCutoffPostingRequestFieldSets {
  private static final Set<String> PREPAYMENT_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID,
          ProtocolBusinessEventFields.AccrualCutoff.PREPAYMENT_ASSET_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL);
  private static final Set<String> DEFERRED_REVENUE_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.AccrualCutoff.DEFERRED_REVENUE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL);
  private static final Set<String> ACCRUED_EXPENSE_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID,
          ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.AccrualCutoff.ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT);
  private static final Set<String> RECOGNITION_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID,
          ProtocolBusinessEventFields.Core.AMOUNT);
  private static final Set<String> SETTLEMENT_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT);

  private ProtocolAccrualCutoffPostingRequestFieldSets() {}

  /** Returns accepted fields for one prepayment request. */
  public static Set<String> prepaymentFields() {
    return PREPAYMENT_FIELDS;
  }

  /** Returns accepted fields for one deferred-revenue request. */
  public static Set<String> deferredRevenueFields() {
    return DEFERRED_REVENUE_FIELDS;
  }

  /** Returns accepted fields for one accrued-expense request. */
  public static Set<String> accruedExpenseFields() {
    return ACCRUED_EXPENSE_FIELDS;
  }

  /** Returns accepted fields for one deferred-balance recognition request. */
  public static Set<String> recognitionFields() {
    return RECOGNITION_FIELDS;
  }

  /** Returns accepted fields for one accrued-expense settlement request. */
  public static Set<String> settlementFields() {
    return SETTLEMENT_FIELDS;
  }
}
