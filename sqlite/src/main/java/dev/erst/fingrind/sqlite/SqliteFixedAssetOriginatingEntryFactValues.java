package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDepreciation;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDisposal;
import org.jspecify.annotations.Nullable;

/** Maps fixed-asset entry facts to the scalar provenance columns retained with a posting. */
final class SqliteFixedAssetOriginatingEntryFactValues {
  private SqliteFixedAssetOriginatingEntryFactValues() {}

  static SqliteOriginatingEntryFactMapper.OriginatingEntryFactValues originatingEntryFactValues(
      FixedAssetBookkeepingEntryVariants entry) {
    return switch (entry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization capitalization ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              capitalization.assetAccountCode().value(),
              capitalization.cashAccountCode().value(),
              capitalization.cost(),
              null);
      case FixedAssetBookkeepingEntryVariants.Depreciation depreciation ->
          depreciationFactValues(depreciation.resolvedDepreciation());
      case FixedAssetBookkeepingEntryVariants.Disposal disposal -> disposalFactValues(disposal);
    };
  }

  private static SqliteOriginatingEntryFactMapper.OriginatingEntryFactValues depreciationFactValues(
      @Nullable ResolvedFixedAssetDepreciation resolved) {
    ResolvedFixedAssetDepreciation required =
        java.util.Objects.requireNonNull(
            resolved, "fixed-asset depreciation requires executor resolution");
    return SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
        required.depreciationExpenseAccountCode().value(),
        required.accumulatedDepreciationAccountCode().value(),
        required.amount(),
        null);
  }

  private static SqliteOriginatingEntryFactMapper.OriginatingEntryFactValues disposalFactValues(
      FixedAssetBookkeepingEntryVariants.Disposal disposal) {
    ResolvedFixedAssetDisposal required =
        java.util.Objects.requireNonNull(
            disposal.resolvedDisposal(), "fixed-asset disposal requires executor resolution");
    return SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
        disposal.cashAccountCode().value(),
        required.assetAccountCode().value(),
        disposal.proceeds(),
        null);
  }
}
