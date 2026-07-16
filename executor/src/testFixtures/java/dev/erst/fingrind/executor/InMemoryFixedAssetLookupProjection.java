package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.FixedAssetRecord;
import dev.erst.fingrind.executor.spi.FixedAssetLookupStore;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reconstructs the fixed-asset register for in-memory executor tests. */
interface InMemoryFixedAssetLookupProjection
    extends FixedAssetLookupStore, InMemoryOwnedLifecycleProjectionSource {
  @Override
  default Optional<FixedAssetRecord> findFixedAsset(FixedAssetId fixedAssetId) {
    Objects.requireNonNull(fixedAssetId, "fixedAssetId");
    return fixedAssets(Optional.empty()).stream()
        .filter(asset -> asset.fixedAssetId().equals(fixedAssetId))
        .findFirst();
  }

  @Override
  default boolean hasFixedAsset(FixedAssetId fixedAssetId) {
    Objects.requireNonNull(fixedAssetId, "fixedAssetId");
    return InMemoryOwnedLifecycleEntries.historyContains(
        this,
        entry ->
            entry instanceof FixedAssetBookkeepingEntryVariants.Capitalization capitalization
                && capitalization.fixedAssetId().equals(fixedAssetId));
  }

  @Override
  default List<FixedAssetRecord> fixedAssets(Optional<LocalDate> effectiveDateAsOf) {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    Map<FixedAssetId, FixedAssetRecord> records = InMemoryBookSessionSupport.mutableMap();
    InMemoryOwnedLifecycleEntries.activeEntries(this, effectiveDateAsOf)
        .forEach(entry -> apply(records, entry));
    return records.values().stream()
        .sorted(
            Comparator.comparing(FixedAssetRecord::capitalizedOn)
                .thenComparing(record -> record.fixedAssetId().value()))
        .toList();
  }

  private static void apply(Map<FixedAssetId, FixedAssetRecord> records, BookkeepingEntry entry) {
    switch (entry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization capitalization ->
          records.put(
              capitalization.fixedAssetId(),
              new FixedAssetRecord(
                  capitalization.fixedAssetId(),
                  capitalization.effectiveDate(),
                  capitalization.assetAccountCode(),
                  capitalization.accumulatedDepreciationAccountCode(),
                  capitalization.depreciationExpenseAccountCode(),
                  capitalization.disposalGainAccountCode(),
                  capitalization.disposalLossAccountCode(),
                  capitalization.cost().toMoney(),
                  capitalization.depreciationSchedule(),
                  Money.zero(capitalization.cost().toMoney().currencyUnit()),
                  0,
                  Optional.empty(),
                  Optional.empty()));
      case FixedAssetBookkeepingEntryVariants.Depreciation depreciation ->
          records.computeIfPresent(
              depreciation.fixedAssetId(),
              (ignored, asset) ->
                  new FixedAssetRecord(
                      asset.fixedAssetId(),
                      asset.capitalizedOn(),
                      asset.assetAccountCode(),
                      asset.accumulatedDepreciationAccountCode(),
                      asset.depreciationExpenseAccountCode(),
                      asset.disposalGainAccountCode(),
                      asset.disposalLossAccountCode(),
                      asset.cost(),
                      asset.depreciationSchedule(),
                      asset
                          .accumulatedDepreciation()
                          .plus(
                              Objects.requireNonNull(
                                      depreciation.resolvedDepreciation(), "resolvedDepreciation")
                                  .amount()
                                  .toMoney()),
                      asset.depreciationPeriodsApplied() + 1,
                      Optional.of(depreciation.effectiveDate()),
                      asset.disposedOn()));
      case FixedAssetBookkeepingEntryVariants.Disposal disposal ->
          records.computeIfPresent(
              disposal.fixedAssetId(),
              (ignored, asset) ->
                  new FixedAssetRecord(
                      asset.fixedAssetId(),
                      asset.capitalizedOn(),
                      asset.assetAccountCode(),
                      asset.accumulatedDepreciationAccountCode(),
                      asset.depreciationExpenseAccountCode(),
                      asset.disposalGainAccountCode(),
                      asset.disposalLossAccountCode(),
                      asset.cost(),
                      asset.depreciationSchedule(),
                      asset.accumulatedDepreciation(),
                      asset.depreciationPeriodsApplied(),
                      Optional.of(disposal.effectiveDate()),
                      Optional.of(disposal.effectiveDate())));
      default -> {}
    }
  }
}
