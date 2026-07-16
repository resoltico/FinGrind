package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;

/** Role-account expectations owned by accrual cut-off events. */
final class PostEntryAccrualCutoffAccountExpectations {
  private PostEntryAccrualCutoffAccountExpectations() {}

  static PostEntryAccountExpectation prepaymentAsset(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode,
        field,
        AccountType.ASSET,
        FinancialPositionLineClassification.PREPAID_EXPENSE,
        CashFlowAssetClassification.NON_CASH,
        AccountRole.PREPAID_EXPENSE);
  }

  static PostEntryAccountExpectation deferredRevenue(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode,
        field,
        AccountType.LIABILITY,
        FinancialPositionLineClassification.DEFERRED_REVENUE,
        null,
        AccountRole.DEFERRED_REVENUE);
  }

  static PostEntryAccountExpectation accruedExpense(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode,
        field,
        AccountType.LIABILITY,
        FinancialPositionLineClassification.ACCRUED_EXPENSE,
        null,
        AccountRole.ACCRUED_EXPENSE);
  }
}
