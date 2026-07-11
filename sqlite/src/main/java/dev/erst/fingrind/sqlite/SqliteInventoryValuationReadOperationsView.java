package dev.erst.fingrind.sqlite;

/** Narrow access to the SQLite inventory-ledger valuation read owner. */
@FunctionalInterface
interface SqliteInventoryValuationReadOperationsView {
  /** Returns the owner of durable inventory-ledger valuation reads. */
  SqliteInventoryValuationReadOperations storeInventoryValuationReadOperations();
}
