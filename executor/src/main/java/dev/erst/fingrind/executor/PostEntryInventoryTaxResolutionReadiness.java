package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import java.util.Optional;

/** Resolution-readiness policy for inventory costing and tax-bearing ordinary entries. */
final class PostEntryInventoryTaxResolutionReadiness {
  private PostEntryInventoryTaxResolutionReadiness() {}

  static Optional<Boolean> readiness(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.SaleSettled sale ->
          Optional.of(
              (sale.taxSelection() == null || sale.appliedTax() != null)
                  && (sale.inventoryRelief() == null || sale.resolvedInventoryCosting() != null));
      case BookkeepingEntry.SaleOnCredit sale ->
          Optional.of(
              (sale.taxSelection() == null || sale.appliedTax() != null)
                  && (sale.inventoryRelief() == null || sale.resolvedInventoryCosting() != null));
      case BookkeepingEntry.PurchaseSettled purchase ->
          Optional.of(
              purchase.resolvedInventoryAcquisition() != null
                  && (purchase.taxSelection() == null || purchase.appliedTax() != null));
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          Optional.of(
              purchase.resolvedInventoryAcquisition() != null
                  && (purchase.taxSelection() == null || purchase.appliedTax() != null));
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization ->
          Optional.of(capitalization.taxSelection() == null || capitalization.appliedTax() != null);
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization ->
          Optional.of(capitalization.taxSelection() == null || capitalization.appliedTax() != null);
      case InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage ->
          Optional.of(shrinkage.resolvedInventoryDisposal() != null);
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease ->
          Optional.of(countIncrease.resolvedInventoryAcquisition() != null);
      case BookkeepingEntry.ExpenseSettled expense ->
          Optional.of(expense.taxSelection() == null || expense.appliedTax() != null);
      case BookkeepingEntry.ExpenseOnCredit expense ->
          Optional.of(expense.taxSelection() == null || expense.appliedTax() != null);
      default -> Optional.empty();
    };
  }
}
