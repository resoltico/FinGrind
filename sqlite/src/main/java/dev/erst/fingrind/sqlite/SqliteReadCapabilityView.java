package dev.erst.fingrind.sqlite;

/** Shared read-side delegation defaults for SQLite capability wrappers. */
interface SqliteReadCapabilityView
    extends SqliteReadSession,
        SqliteReadAccountCatalogCapabilityView,
        SqliteReadTaxCatalogCapabilityView,
        SqliteReadPostingLookupCapabilityView,
        SqliteReadPostingRangeCapabilityView,
        SqliteReadInventoryCapabilityView,
        SqliteReadAccrualCutoffCapabilityView,
        SqliteReadFixedAssetCapabilityView,
        SqliteReadFinancingCapabilityView,
        SqliteReadRealizedForeignExchangeCapabilityView,
        SqliteReadLatvianPayrollCapabilityView {
  @Override
  default java.util.List<dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord>
      inventoryValuationMovements(java.util.Optional<java.time.LocalDate> effectiveDateAsOf) {
    return SqliteReadInventoryCapabilityView.super.inventoryValuationMovements(effectiveDateAsOf);
  }
}
