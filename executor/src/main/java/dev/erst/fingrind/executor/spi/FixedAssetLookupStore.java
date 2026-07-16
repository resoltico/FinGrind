package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.executor.bookkeeping.FixedAssetRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Loads one fixed-asset aggregate for executor admission and report projection. */
public interface FixedAssetLookupStore {
  /** Returns the durable fixed-asset aggregate for the selected identifier, when it exists. */
  default Optional<FixedAssetRecord> findFixedAsset(FixedAssetId fixedAssetId) {
    return Optional.empty();
  }

  /** Returns whether an identifier has ever been retained by this book's fixed-asset history. */
  default boolean hasFixedAsset(FixedAssetId fixedAssetId) {
    return false;
  }

  /** Returns every active fixed-asset aggregate for register projection. */
  default List<FixedAssetRecord> fixedAssets(Optional<LocalDate> effectiveDateAsOf) {
    java.util.Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    return List.of();
  }
}
