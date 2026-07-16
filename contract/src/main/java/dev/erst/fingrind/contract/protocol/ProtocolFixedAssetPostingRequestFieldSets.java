package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical post-entry field sets owned by Fixed Assets. */
public final class ProtocolFixedAssetPostingRequestFieldSets {
  private static final Set<String> CAPITALIZATION_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolPostEntryFields.TopLevel.FIXED_ASSET_ID,
          ProtocolPostEntryFields.TopLevel.ASSET_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.ACCUMULATED_DEPRECIATION_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.DEPRECIATION_EXPENSE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.DISPOSAL_GAIN_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.DISPOSAL_LOSS_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.COST,
          ProtocolPostEntryFields.TopLevel.DEPRECIATION_SCHEDULE);
  private static final Set<String> DEPRECIATION_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolPostEntryFields.TopLevel.FIXED_ASSET_ID);
  private static final Set<String> DISPOSAL_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolPostEntryFields.TopLevel.FIXED_ASSET_ID,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.PROCEEDS);

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
