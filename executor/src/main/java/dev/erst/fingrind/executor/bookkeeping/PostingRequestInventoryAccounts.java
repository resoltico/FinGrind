package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import java.util.Set;

/** Adds typed inventory-event accounts to a posting request's canonical account set. */
final class PostingRequestInventoryAccounts {
  private PostingRequestInventoryAccounts() {}

  static void add(Set<AccountCode> accounts, InventoryBookkeepingEntryVariants entry) {
    switch (entry) {
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization -> {
        accounts.add(capitalization.inventoryAccountCode());
        accounts.add(capitalization.cashAccountCode());
      }
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization -> {
        accounts.add(capitalization.inventoryAccountCode());
        accounts.add(capitalization.payableAccountCode());
      }
      case InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown -> {
        accounts.add(writeDown.inventoryAccountCode());
        accounts.add(writeDown.writeDownLossAccountCode());
      }
      case InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage -> {
        accounts.add(shrinkage.inventoryAccountCode());
        accounts.add(shrinkage.shrinkageLossAccountCode());
      }
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease -> {
        accounts.add(countIncrease.inventoryAccountCode());
        accounts.add(countIncrease.countGainAccountCode());
      }
    }
  }
}
