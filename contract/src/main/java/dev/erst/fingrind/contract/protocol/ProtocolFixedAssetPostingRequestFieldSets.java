package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical post-entry field sets owned by Fixed Assets. */
public final class ProtocolFixedAssetPostingRequestFieldSets {
  private static final Set<String> CAPITALIZATION_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.FixedAsset.FIXED_ASSET_ID,
          ProtocolBusinessEventFields.FixedAsset.ASSET_ACCOUNT_CODE,
          ProtocolBusinessEventFields.FixedAsset.ACCUMULATED_DEPRECIATION_ACCOUNT_CODE,
          ProtocolBusinessEventFields.FixedAsset.DEPRECIATION_EXPENSE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.FixedAsset.DISPOSAL_GAIN_ACCOUNT_CODE,
          ProtocolBusinessEventFields.FixedAsset.DISPOSAL_LOSS_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.FixedAsset.COST,
          ProtocolBusinessEventFields.FixedAsset.DEPRECIATION_SCHEDULE);
  private static final Set<String> DEPRECIATION_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.FixedAsset.FIXED_ASSET_ID);
  private static final Set<String> DISPOSAL_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.FixedAsset.FIXED_ASSET_ID,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.FixedAsset.PROCEEDS);

  private ProtocolFixedAssetPostingRequestFieldSets() {}

  /** Returns accepted fields for fixed-asset capitalization. */
  public static Set<String> capitalizationFields() {
    return CAPITALIZATION_FIELDS;
  }

  /** Returns accepted fields for fixed-asset depreciation. */
  public static Set<String> depreciationFields() {
    return DEPRECIATION_FIELDS;
  }

  /** Returns accepted fields for fixed-asset disposal. */
  public static Set<String> disposalFields() {
    return DISPOSAL_FIELDS;
  }
}
