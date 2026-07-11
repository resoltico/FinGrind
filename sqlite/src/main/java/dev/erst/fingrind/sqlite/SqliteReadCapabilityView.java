package dev.erst.fingrind.sqlite;

/** Shared read-side delegation defaults for SQLite capability wrappers. */
interface SqliteReadCapabilityView
    extends SqliteReadSession,
        dev.erst.fingrind.executor.spi.InventoryMovementLookupStore,
        dev.erst.fingrind.executor.spi.InventoryStateLookupStore,
        SqliteReadAccountCatalogCapabilityView,
        SqliteReadTaxCatalogCapabilityView,
        SqliteReadPostingLookupCapabilityView,
        SqliteReadReportingCapabilityView,
        SqliteInventoryValuationReadOperationsView {
  @Override
  default dev.erst.fingrind.executor.spi.BookLifecycleInspection inspectBook() {
    return SqliteReadAccountCatalogCapabilityView.super.inspectBook();
  }

  @Override
  default boolean allowsInitializedWorkflow() {
    return SqliteReadAccountCatalogCapabilityView.super.allowsInitializedWorkflow();
  }

  @Override
  default dev.erst.fingrind.core.BookIdentity requireInitializedBookIdentity() {
    return SqliteReadAccountCatalogCapabilityView.super.requireInitializedBookIdentity();
  }

  @Override
  default java.util.List<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> postings(
      dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
    return SqliteReadReportingCapabilityView.super.postings(effectiveDateRange);
  }

  @Override
  default java.util.Optional<java.time.LocalDate> earliestPostingEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().earliestPostingEffectiveDate();
  }

  @Override
  default java.util.Optional<java.time.LocalDate> transferredThroughEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().transferredThroughEffectiveDate();
  }

  @Override
  default java.util.Optional<dev.erst.fingrind.executor.bookkeeping.InventoryAccountState>
      findInventoryAccountState(dev.erst.fingrind.core.AccountCode inventoryAccountCode) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().findInventoryAccountState(inventoryAccountCode);
  }

  @Override
  default java.util.List<dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord>
      inventoryMovements(dev.erst.fingrind.core.PostingId postingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().inventoryMovements(postingId);
  }

  @Override
  default java.util.List<dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord>
      inventoryValuationMovements(java.util.Optional<java.time.LocalDate> effectiveDateAsOf) {
    storeThreadOwner().requireOwnerThread();
    return storeInventoryValuationReadOperations().inventoryValuationMovements(effectiveDateAsOf);
  }
}
