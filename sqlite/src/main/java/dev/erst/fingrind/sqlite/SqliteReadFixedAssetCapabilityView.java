package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.executor.bookkeeping.FixedAssetRecord;
import dev.erst.fingrind.executor.spi.FixedAssetLookupStore;
import java.util.List;
import java.util.Optional;

/** Fixed-asset lifecycle defaults for SQLite read wrappers. */
interface SqliteReadFixedAssetCapabilityView
    extends FixedAssetLookupStore, SqlitePostingFactStoreReadOperationsView {
  @Override
  default Optional<FixedAssetRecord> findFixedAsset(FixedAssetId fixedAssetId) {
    return fixedAssets(java.util.Optional.empty()).stream()
        .filter(asset -> asset.fixedAssetId().equals(fixedAssetId))
        .findFirst();
  }

  @Override
  default boolean hasFixedAsset(FixedAssetId fixedAssetId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().fixedAssets().hasFixedAsset(fixedAssetId);
  }

  @Override
  default List<FixedAssetRecord> fixedAssets(
      java.util.Optional<java.time.LocalDate> effectiveDateAsOf) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().fixedAssets().fixedAssets(effectiveDateAsOf);
  }
}
