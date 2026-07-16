package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.bookkeeping.BookTemplateSeedAccountFactory.postable;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.Optional;

/** Built-in revenue and expense declarations shared by template doctrines. */
final class BookTemplateProfitAndLossSeedAccounts {
  private BookTemplateProfitAndLossSeedAccounts() {}

  static AccountDeclaration serviceRevenueAccount() {
    return postable(
        "service-revenue",
        "Service Revenue",
        AccountType.REVENUE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
  }

  static AccountDeclaration salesRevenueAccount() {
    return postable(
        "sales-revenue",
        "Sales Revenue",
        AccountType.REVENUE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
  }

  static AccountDeclaration salesDiscountAllowanceAccount() {
    return postable(
        "sales-discount-allowance",
        "Sales Discount Allowance",
        AccountType.REVENUE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE));
  }

  static AccountDeclaration operatingExpenseAccount() {
    return postable(
        "operating-expense",
        "Operating Expense",
        AccountType.EXPENSE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
  }

  static AccountDeclaration costOfSalesAccount() {
    return postable(
        "cost-of-sales",
        "Cost of Sales",
        AccountType.EXPENSE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.COST_OF_SALES));
  }

  static AccountDeclaration settlementFeeAccount() {
    return postable(
        "settlement-fee",
        "Settlement Fee",
        AccountType.EXPENSE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.SETTLEMENT_FEE));
  }

  static AccountDeclaration badDebtWriteOffAccount() {
    return postable(
        "bad-debt-write-off",
        "Bad Debt Write-Off",
        AccountType.EXPENSE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(ProfitAndLossLineClassification.BAD_DEBT_WRITE_OFF));
  }
}
