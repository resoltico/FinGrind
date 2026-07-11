package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.AccountCode;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Resolves referenced accounts for typed inventory business events. */
final class PostEntryInventorySemanticContext {
  private PostEntryInventorySemanticContext() {}

  static Set<AccountCode> referencedAccounts(InventoryBookkeepingEntryVariants entry) {
    return switch (entry) {
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization ->
          accountSet(
              capitalization.inventoryAccountCode(),
              capitalization.cashAccountCode(),
              taxAccountCode(capitalization.appliedTax()));
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization ->
          accountSet(
              capitalization.inventoryAccountCode(),
              capitalization.payableAccountCode(),
              taxAccountCode(capitalization.appliedTax()));
      case InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown ->
          accountSet(writeDown.inventoryAccountCode(), writeDown.writeDownLossAccountCode(), null);
      case InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage ->
          accountSet(shrinkage.inventoryAccountCode(), shrinkage.shrinkageLossAccountCode(), null);
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease ->
          accountSet(
              countIncrease.inventoryAccountCode(), countIncrease.countGainAccountCode(), null);
    };
  }

  private static Set<AccountCode> accountSet(
      AccountCode first, AccountCode second, @Nullable AccountCode third) {
    Set<AccountCode> accounts = new LinkedHashSet<>();
    accounts.add(first);
    accounts.add(second);
    if (third != null) {
      accounts.add(third);
    }
    return accounts;
  }

  private static @Nullable AccountCode taxAccountCode(@Nullable AppliedTax appliedTax) {
    return appliedTax == null ? null : appliedTax.taxAccountCode();
  }
}
