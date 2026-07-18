package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.Optional;

/** Trading-only inventory adjustment accounts that complete the inventory operating model. */
final class TradingInventorySeedAccounts {
  private TradingInventorySeedAccounts() {}

  static AccountDeclaration writeDownLossAccount() {
    return operatingExpense("inventory-write-down-loss", "Inventory Write-Down Loss");
  }

  static AccountDeclaration shrinkageLossAccount() {
    return operatingExpense("inventory-shrinkage-loss", "Inventory Shrinkage Loss");
  }

  static AccountDeclaration countGainAccount() {
    return new AccountDeclaration(
        new AccountCode("inventory-count-gain"),
        new AccountName("Inventory Count Gain"),
        AccountType.REVENUE,
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.OTHER_REVENUE),
            Optional.empty()));
  }

  private static AccountDeclaration operatingExpense(String code, String name) {
    return new AccountDeclaration(
        new AccountCode(code),
        new AccountName(name),
        AccountType.EXPENSE,
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE),
            Optional.empty()));
  }
}
