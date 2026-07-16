package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.bookkeeping.BookTemplateAssetLiabilitySeedAccounts.accountsPayableAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateAssetLiabilitySeedAccounts.accountsReceivableAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateAssetLiabilitySeedAccounts.accruedExpenseAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateAssetLiabilitySeedAccounts.cashAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateAssetLiabilitySeedAccounts.deferredRevenueAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateAssetLiabilitySeedAccounts.inventoryAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateAssetLiabilitySeedAccounts.prepaidExpenseAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateEquitySeedAccounts.ownerCapitalAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateEquitySeedAccounts.ownerDrawsAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateEquitySeedAccounts.resultHoldingAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateEquitySeedAccounts.retainedAccumulatedAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateProfitAndLossSeedAccounts.badDebtWriteOffAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateProfitAndLossSeedAccounts.costOfSalesAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateProfitAndLossSeedAccounts.operatingExpenseAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateProfitAndLossSeedAccounts.salesDiscountAllowanceAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateProfitAndLossSeedAccounts.salesRevenueAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateProfitAndLossSeedAccounts.serviceRevenueAccount;
import static dev.erst.fingrind.executor.bookkeeping.BookTemplateProfitAndLossSeedAccounts.settlementFeeAccount;

import dev.erst.fingrind.core.BookDoctrine;
import java.util.List;
import java.util.Objects;

/** Canonical account declarations for built-in book templates. */
public final class BookTemplateAccounts {
  private BookTemplateAccounts() {}

  /** Returns the canonical account set for the supplied built-in doctrine. */
  public static List<AccountDeclaration> declarations(BookDoctrine bookDoctrine) {
    BookDoctrine doctrine = Objects.requireNonNull(bookDoctrine, "bookDoctrine");
    return switch (doctrine.bookTemplateId()) {
      case OWNER_MANAGED_SERVICE ->
          switch (doctrine.accountingBasis()) {
            case CASH -> ownerManagedServiceCash();
            case ACCRUAL -> ownerManagedServiceAccrual();
          };
      case OWNER_MANAGED_TRADING ->
          switch (doctrine.accountingBasis()) {
            case CASH -> ownerManagedTradingCash();
            case ACCRUAL -> ownerManagedTradingAccrual();
          };
    };
  }

  private static List<AccountDeclaration> ownerManagedServiceCash() {
    return List.of(
        cashAccount(),
        ownerCapitalAccount(),
        ownerDrawsAccount(),
        resultHoldingAccount(),
        retainedAccumulatedAccount(),
        serviceRevenueAccount(),
        operatingExpenseAccount());
  }

  private static List<AccountDeclaration> ownerManagedServiceAccrual() {
    return List.of(
        cashAccount(),
        accountsReceivableAccount(),
        accountsPayableAccount(),
        prepaidExpenseAccount(),
        deferredRevenueAccount(),
        accruedExpenseAccount(),
        ownerCapitalAccount(),
        ownerDrawsAccount(),
        resultHoldingAccount(),
        retainedAccumulatedAccount(),
        serviceRevenueAccount(),
        salesDiscountAllowanceAccount(),
        operatingExpenseAccount(),
        settlementFeeAccount(),
        badDebtWriteOffAccount());
  }

  private static List<AccountDeclaration> ownerManagedTradingCash() {
    return List.of(
        cashAccount(),
        inventoryAccount(),
        ownerCapitalAccount(),
        ownerDrawsAccount(),
        resultHoldingAccount(),
        retainedAccumulatedAccount(),
        salesRevenueAccount(),
        salesDiscountAllowanceAccount(),
        costOfSalesAccount(),
        TradingInventorySeedAccounts.writeDownLossAccount(),
        TradingInventorySeedAccounts.shrinkageLossAccount(),
        TradingInventorySeedAccounts.countGainAccount(),
        operatingExpenseAccount());
  }

  private static List<AccountDeclaration> ownerManagedTradingAccrual() {
    return List.of(
        cashAccount(),
        inventoryAccount(),
        accountsReceivableAccount(),
        accountsPayableAccount(),
        prepaidExpenseAccount(),
        deferredRevenueAccount(),
        accruedExpenseAccount(),
        ownerCapitalAccount(),
        ownerDrawsAccount(),
        resultHoldingAccount(),
        retainedAccumulatedAccount(),
        salesRevenueAccount(),
        salesDiscountAllowanceAccount(),
        costOfSalesAccount(),
        TradingInventorySeedAccounts.writeDownLossAccount(),
        TradingInventorySeedAccounts.shrinkageLossAccount(),
        TradingInventorySeedAccounts.countGainAccount(),
        operatingExpenseAccount(),
        settlementFeeAccount(),
        badDebtWriteOffAccount());
  }
}
