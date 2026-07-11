package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Owns durable inventory-ledger reads used to reconstruct exact valuation. */
final class SqliteInventoryValuationReadOperations {
  private final SqliteStoreReportOperations reportOperations;

  SqliteInventoryValuationReadOperations(
      SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.reportOperations =
        new SqliteStoreReportOperations(
            Objects.requireNonNull(context, "context"),
            Objects.requireNonNull(lifecycle, "lifecycle"));
  }

  /** Returns canonical inventory movements through an optional effective-date cutoff. */
  List<InventoryValuationMovementRecord> inventoryValuationMovements(
      Optional<LocalDate> effectiveDateAsOf) {
    return reportOperations.inventoryValuationMovements(
        Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf"));
  }
}
