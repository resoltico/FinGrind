package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Package-private typed-entry account-role descriptors derived from declared role fields. */
final class PostEntryRoleAccountExpectations {
  record DistinctAccountPair(
      AccountCode firstAccountCode,
      String firstField,
      AccountCode secondAccountCode,
      String secondField) {
    DistinctAccountPair {
      Objects.requireNonNull(firstAccountCode, "firstAccountCode");
      Objects.requireNonNull(firstField, "firstField");
      Objects.requireNonNull(secondAccountCode, "secondAccountCode");
      Objects.requireNonNull(secondField, "secondField");
    }
  }

  record AccountExpectation(
      AccountCode accountCode,
      String field,
      @Nullable AccountType expectedAccountType,
      @Nullable FinancialPositionLineClassification expectedFinancialPositionClassification,
      @Nullable CashFlowAssetClassification expectedCashFlowAssetClassification,
      @Nullable AccountRole expectedAccountRole) {
    AccountExpectation {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(field, "field");
    }
  }

  private PostEntryRoleAccountExpectations() {}

  static DistinctAccountPair distinct(
      AccountCode firstAccountCode,
      String firstField,
      AccountCode secondAccountCode,
      String secondField) {
    return new DistinctAccountPair(firstAccountCode, firstField, secondAccountCode, secondField);
  }

  static DistinctAccountPair distinct(
      AccountExpectation firstExpectation, AccountExpectation secondExpectation) {
    return distinct(
        firstExpectation.accountCode(),
        firstExpectation.field(),
        secondExpectation.accountCode(),
        secondExpectation.field());
  }

  static AccountExpectation cash(AccountCode accountCode, String field) {
    return new AccountExpectation(
        accountCode,
        field,
        AccountType.ASSET,
        null,
        CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT,
        null);
  }

  static AccountExpectation revenue(AccountCode accountCode, String field) {
    return new AccountExpectation(accountCode, field, AccountType.REVENUE, null, null, null);
  }

  static AccountExpectation expense(AccountCode accountCode, String field) {
    return new AccountExpectation(accountCode, field, AccountType.EXPENSE, null, null, null);
  }

  static AccountExpectation nonCashAsset(AccountCode accountCode, String field) {
    return new AccountExpectation(
        accountCode, field, AccountType.ASSET, null, CashFlowAssetClassification.NON_CASH, null);
  }

  static AccountExpectation inventory(AccountCode accountCode, String field) {
    return new AccountExpectation(
        accountCode,
        field,
        AccountType.ASSET,
        FinancialPositionLineClassification.INVENTORY,
        CashFlowAssetClassification.NON_CASH,
        AccountRole.INVENTORY);
  }

  static AccountExpectation receivable(AccountCode accountCode, String field) {
    return new AccountExpectation(
        accountCode,
        field,
        AccountType.ASSET,
        FinancialPositionLineClassification.TRADE_RECEIVABLE,
        null,
        null);
  }

  static AccountExpectation payable(AccountCode accountCode, String field) {
    return new AccountExpectation(
        accountCode,
        field,
        AccountType.LIABILITY,
        FinancialPositionLineClassification.TRADE_PAYABLE,
        null,
        null);
  }

  static AccountExpectation equityContribution(AccountCode accountCode, String field) {
    return new AccountExpectation(
        accountCode,
        field,
        AccountType.EQUITY,
        FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
        null,
        null);
  }

  static AccountExpectation equityWithdrawal(AccountCode accountCode, String field) {
    return new AccountExpectation(
        accountCode,
        field,
        AccountType.EQUITY,
        FinancialPositionLineClassification.EQUITY_WITHDRAWAL,
        null,
        null);
  }

  static AccountExpectation settlementAdjunct(AccountCode accountCode, String field) {
    return new AccountExpectation(
        accountCode, field, null, null, null, AccountRole.SETTLEMENT_ADJUNCT);
  }
}
