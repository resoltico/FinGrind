package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.Map;

/** Fixed-asset entry kinds and their owned event classes. */
final class FixedAssetTypedEntryEventClasses {
  private FixedAssetTypedEntryEventClasses() {}

  static Map<BookkeepingEntryKind, EconomicEventClass> eventClasses() {
    return Map.of(
        BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION,
        EconomicEventClass.FIXED_ASSET_CAPITALIZATION,
        BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION,
        EconomicEventClass.FIXED_ASSET_DEPRECIATION,
        BookkeepingEntryKind.FIXED_ASSET_DISPOSAL,
        EconomicEventClass.FIXED_ASSET_DISPOSAL);
  }
}
