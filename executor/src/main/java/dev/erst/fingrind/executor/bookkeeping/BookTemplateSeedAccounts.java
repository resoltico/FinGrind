package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.Optional;

/** Package-local seeded account declarations shared by built-in book templates. */
final class BookTemplateSeedAccounts {
  private BookTemplateSeedAccounts() {}

  static AccountDeclaration cashAccount() {
    return account(
        "cash",
        "Cash",
        AccountType.ASSET,
        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
        Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT),
        Optional.empty());
  }

  static AccountDeclaration accountsReceivableAccount() {
    return account(
        "accounts-receivable",
        "Accounts Receivable",
        AccountType.ASSET,
        Optional.of(FinancialPositionLineClassification.TRADE_RECEIVABLE),
        Optional.of(CashFlowAssetClassification.NON_CASH),
        Optional.empty());
  }

  static AccountDeclaration inventoryAccount() {
    return account(
        "inventory",
        "Inventory",
        AccountType.ASSET,
        Optional.of(FinancialPositionLineClassification.INVENTORY),
        Optional.of(CashFlowAssetClassification.NON_CASH),
        Optional.empty());
  }

  static AccountDeclaration accountsPayableAccount() {
    return account(
        "accounts-payable",
        "Accounts Payable",
        AccountType.LIABILITY,
        Optional.of(FinancialPositionLineClassification.TRADE_PAYABLE),
        Optional.empty(),
        Optional.empty());
  }

  static AccountDeclaration ownerCapitalAccount() {
    return account(
        "owner-capital",
        "Owner Capital",
        AccountType.EQUITY,
        Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
        Optional.empty(),
        Optional.empty());
  }

  static AccountDeclaration ownerDrawsAccount() {
    return account(
        "owner-draws",
        "Owner Draws",
        AccountType.EQUITY,
        Optional.of(FinancialPositionLineClassification.EQUITY_WITHDRAWAL),
        Optional.empty(),
        Optional.empty());
  }

  static AccountDeclaration resultHoldingAccount() {
    return account(
        "result-holding",
        "Result Holding",
        AccountType.EQUITY,
        Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
        Optional.empty(),
        Optional.empty());
  }

  static AccountDeclaration retainedAccumulatedAccount() {
    return account(
        "retained-accumulated",
        "Retained Accumulated",
        AccountType.EQUITY,
        Optional.of(FinancialPositionLineClassification.RETAINED_ACCUMULATED),
        Optional.empty(),
        Optional.empty());
  }

  static AccountDeclaration serviceRevenueAccount() {
    return account(
        "service-revenue",
        "Service Revenue",
        AccountType.REVENUE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
  }

  static AccountDeclaration salesRevenueAccount() {
    return account(
        "sales-revenue",
        "Sales Revenue",
        AccountType.REVENUE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
  }

  static AccountDeclaration salesDiscountAllowanceAccount() {
    return account(
        "sales-discount-allowance",
        "Sales Discount Allowance",
        AccountType.REVENUE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE));
  }

  static AccountDeclaration operatingExpenseAccount() {
    return account(
        "operating-expense",
        "Operating Expense",
        AccountType.EXPENSE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
  }

  static AccountDeclaration costOfSalesAccount() {
    return account(
        "cost-of-sales",
        "Cost of Sales",
        AccountType.EXPENSE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.COST_OF_SALES));
  }

  static AccountDeclaration settlementFeeAccount() {
    return account(
        "settlement-fee",
        "Settlement Fee",
        AccountType.EXPENSE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.SETTLEMENT_FEE));
  }

  static AccountDeclaration badDebtWriteOffAccount() {
    return account(
        "bad-debt-write-off",
        "Bad Debt Write-Off",
        AccountType.EXPENSE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.BAD_DEBT_WRITE_OFF));
  }

  private static AccountDeclaration account(
      String code,
      String name,
      AccountType accountType,
      Optional<FinancialPositionLineClassification> financialPositionLineClassification,
      Optional<CashFlowAssetClassification> cashFlowAssetClassification,
      Optional<ProfitAndLossLineClassification> profitAndLossLineClassification) {
    return new AccountDeclaration(
        new AccountCode(code),
        new AccountName(name),
        accountType,
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            financialPositionLineClassification,
            profitAndLossLineClassification,
            cashFlowAssetClassification));
  }
}
