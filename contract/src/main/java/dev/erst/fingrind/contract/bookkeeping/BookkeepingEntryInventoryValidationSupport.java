package dev.erst.fingrind.contract.bookkeeping;

import org.jspecify.annotations.Nullable;

/** Inventory-resolution validation shared by caller-authored bookkeeping entries. */
final class BookkeepingEntryInventoryValidationSupport {
  private BookkeepingEntryInventoryValidationSupport() {}

  static ResolvedInventoryCosting requireResolvedInventoryCosting(
      @Nullable ResolvedInventoryCosting resolvedInventoryCosting, String entryKind) {
    if (resolvedInventoryCosting == null) {
      throw new IllegalStateException(
          entryKind
              + " inventory relief requires executor-owned inventory costing before journalEntry() can be derived.");
    }
    return resolvedInventoryCosting;
  }

  static ResolvedInventoryAcquisition requireResolvedInventoryAcquisition(
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition, String entryKind) {
    if (resolvedInventoryAcquisition == null) {
      throw new IllegalStateException(
          entryKind
              + " inventory acquisition requires executor-owned quantity resolution before journalEntry() can be derived.");
    }
    return resolvedInventoryAcquisition;
  }

  static ResolvedInventoryDisposal requireResolvedInventoryDisposal(
      @Nullable ResolvedInventoryDisposal resolvedInventoryDisposal, String entryKind) {
    if (resolvedInventoryDisposal == null) {
      throw new IllegalStateException(
          entryKind
              + " inventory shrinkage requires executor-owned carrying-cost resolution before journalEntry() can be derived.");
    }
    return resolvedInventoryDisposal;
  }
}
