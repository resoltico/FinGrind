package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;

/** Role-account expectations shared by ordinary trading and operating events. */
final class PostEntryOperatingAccountExpectations {
  private PostEntryOperatingAccountExpectations() {}

  static PostEntryAccountExpectation cash(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode,
        field,
        AccountType.ASSET,
        null,
        CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT,
        null);
  }

  static PostEntryAccountExpectation revenue(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode, field, AccountType.REVENUE, null, null, null);
  }

  static PostEntryAccountExpectation expense(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode, field, AccountType.EXPENSE, null, null, null);
  }

  static PostEntryAccountExpectation nonCashAsset(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode, field, AccountType.ASSET, null, CashFlowAssetClassification.NON_CASH, null);
  }

  static PostEntryAccountExpectation nonCurrentAsset(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode,
        field,
        AccountType.ASSET,
        FinancialPositionLineClassification.NONCURRENT_ASSET,
        CashFlowAssetClassification.NON_CASH,
        null);
  }

  static PostEntryAccountExpectation inventory(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode,
        field,
        AccountType.ASSET,
        FinancialPositionLineClassification.INVENTORY,
        CashFlowAssetClassification.NON_CASH,
        AccountRole.INVENTORY);
  }

  static PostEntryAccountExpectation receivable(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode,
        field,
        AccountType.ASSET,
        FinancialPositionLineClassification.TRADE_RECEIVABLE,
        null,
        null);
  }

  static PostEntryAccountExpectation payable(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode,
        field,
        AccountType.LIABILITY,
        FinancialPositionLineClassification.TRADE_PAYABLE,
        null,
        null);
  }

  static PostEntryAccountExpectation settlementAdjunct(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode, field, null, null, null, AccountRole.SETTLEMENT_ADJUNCT);
  }
}
