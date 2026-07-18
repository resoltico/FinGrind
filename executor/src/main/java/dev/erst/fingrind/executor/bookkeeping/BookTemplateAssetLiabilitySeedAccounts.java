package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.bookkeeping.BookTemplateSeedAccountFactory.postable;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.Optional;

/** Built-in asset and liability declarations shared by template doctrines. */
final class BookTemplateAssetLiabilitySeedAccounts {
  private BookTemplateAssetLiabilitySeedAccounts() {}

  static AccountDeclaration cashAccount() {
    return postable(
        "cash",
        "Cash",
        AccountType.ASSET,
        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
        Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT),
        Optional.empty());
  }

  static AccountDeclaration accountsReceivableAccount() {
    return postable(
        "accounts-receivable",
        "Accounts Receivable",
        AccountType.ASSET,
        Optional.of(FinancialPositionLineClassification.TRADE_RECEIVABLE),
        Optional.of(CashFlowAssetClassification.NON_CASH),
        Optional.empty());
  }

  static AccountDeclaration inventoryAccount() {
    return new AccountDeclaration(
        new AccountCode("inventory"),
        new AccountName("Inventory"),
        AccountType.ASSET,
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.INVENTORY),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.NON_CASH)),
        new UnitOfMeasure("unit", 0));
  }

  static AccountDeclaration accountsPayableAccount() {
    return postable(
        "accounts-payable",
        "Accounts Payable",
        AccountType.LIABILITY,
        Optional.of(FinancialPositionLineClassification.TRADE_PAYABLE),
        Optional.empty(),
        Optional.empty());
  }

  static AccountDeclaration prepaidExpenseAccount() {
    return postable(
        "prepaid-expense",
        "Prepaid Expense",
        AccountType.ASSET,
        Optional.of(FinancialPositionLineClassification.PREPAID_EXPENSE),
        Optional.of(CashFlowAssetClassification.NON_CASH),
        Optional.empty());
  }

  static AccountDeclaration deferredRevenueAccount() {
    return postable(
        "deferred-revenue",
        "Deferred Revenue",
        AccountType.LIABILITY,
        Optional.of(FinancialPositionLineClassification.DEFERRED_REVENUE),
        Optional.empty(),
        Optional.empty());
  }

  static AccountDeclaration accruedExpenseAccount() {
    return postable(
        "accrued-expense",
        "Accrued Expense",
        AccountType.LIABILITY,
        Optional.of(FinancialPositionLineClassification.ACCRUED_EXPENSE),
        Optional.empty(),
        Optional.empty());
  }
}
