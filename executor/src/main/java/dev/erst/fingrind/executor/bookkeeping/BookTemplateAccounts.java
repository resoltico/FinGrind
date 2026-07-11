package dev.erst.fingrind.executor.bookkeeping;

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
        BookTemplateSeedAccounts.cashAccount(),
        BookTemplateSeedAccounts.ownerCapitalAccount(),
        BookTemplateSeedAccounts.ownerDrawsAccount(),
        BookTemplateSeedAccounts.resultHoldingAccount(),
        BookTemplateSeedAccounts.retainedAccumulatedAccount(),
        BookTemplateSeedAccounts.serviceRevenueAccount(),
        BookTemplateSeedAccounts.operatingExpenseAccount());
  }

  private static List<AccountDeclaration> ownerManagedServiceAccrual() {
    return List.of(
        BookTemplateSeedAccounts.cashAccount(),
        BookTemplateSeedAccounts.accountsReceivableAccount(),
        BookTemplateSeedAccounts.accountsPayableAccount(),
        BookTemplateSeedAccounts.ownerCapitalAccount(),
        BookTemplateSeedAccounts.ownerDrawsAccount(),
        BookTemplateSeedAccounts.resultHoldingAccount(),
        BookTemplateSeedAccounts.retainedAccumulatedAccount(),
        BookTemplateSeedAccounts.serviceRevenueAccount(),
        BookTemplateSeedAccounts.salesDiscountAllowanceAccount(),
        BookTemplateSeedAccounts.operatingExpenseAccount(),
        BookTemplateSeedAccounts.settlementFeeAccount(),
        BookTemplateSeedAccounts.badDebtWriteOffAccount());
  }

  private static List<AccountDeclaration> ownerManagedTradingCash() {
    return List.of(
        BookTemplateSeedAccounts.cashAccount(),
        BookTemplateSeedAccounts.inventoryAccount(),
        BookTemplateSeedAccounts.ownerCapitalAccount(),
        BookTemplateSeedAccounts.ownerDrawsAccount(),
        BookTemplateSeedAccounts.resultHoldingAccount(),
        BookTemplateSeedAccounts.retainedAccumulatedAccount(),
        BookTemplateSeedAccounts.salesRevenueAccount(),
        BookTemplateSeedAccounts.salesDiscountAllowanceAccount(),
        BookTemplateSeedAccounts.costOfSalesAccount(),
        TradingInventorySeedAccounts.writeDownLossAccount(),
        TradingInventorySeedAccounts.shrinkageLossAccount(),
        TradingInventorySeedAccounts.countGainAccount(),
        BookTemplateSeedAccounts.operatingExpenseAccount());
  }

  private static List<AccountDeclaration> ownerManagedTradingAccrual() {
    return List.of(
        BookTemplateSeedAccounts.cashAccount(),
        BookTemplateSeedAccounts.inventoryAccount(),
        BookTemplateSeedAccounts.accountsReceivableAccount(),
        BookTemplateSeedAccounts.accountsPayableAccount(),
        BookTemplateSeedAccounts.ownerCapitalAccount(),
        BookTemplateSeedAccounts.ownerDrawsAccount(),
        BookTemplateSeedAccounts.resultHoldingAccount(),
        BookTemplateSeedAccounts.retainedAccumulatedAccount(),
        BookTemplateSeedAccounts.salesRevenueAccount(),
        BookTemplateSeedAccounts.salesDiscountAllowanceAccount(),
        BookTemplateSeedAccounts.costOfSalesAccount(),
        TradingInventorySeedAccounts.writeDownLossAccount(),
        TradingInventorySeedAccounts.shrinkageLossAccount(),
        TradingInventorySeedAccounts.countGainAccount(),
        BookTemplateSeedAccounts.operatingExpenseAccount(),
        BookTemplateSeedAccounts.settlementFeeAccount(),
        BookTemplateSeedAccounts.badDebtWriteOffAccount());
  }
}
