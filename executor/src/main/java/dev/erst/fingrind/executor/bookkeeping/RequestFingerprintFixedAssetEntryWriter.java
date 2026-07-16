package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;

/** Writes canonical caller-authored fingerprint fields for fixed-asset entries. */
final class RequestFingerprintFixedAssetEntryWriter {
  private RequestFingerprintFixedAssetEntryWriter() {}

  static void append(StringBuilder canonical, FixedAssetBookkeepingEntryVariants entry) {
    switch (entry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization capitalization -> {
        appendId(canonical, capitalization.fixedAssetId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "assetAccountCode", capitalization.assetAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical,
            "accumulatedDepreciationAccountCode",
            capitalization.accumulatedDepreciationAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical,
            "depreciationExpenseAccountCode",
            capitalization.depreciationExpenseAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "disposalGainAccountCode", capitalization.disposalGainAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "disposalLossAccountCode", capitalization.disposalLossAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "cashAccountCode", capitalization.cashAccountCode());
        RequestFingerprintEntryFieldWriter.appendField(
            canonical, "callerAuthoredEntry.costCurrency", capitalization.cost().currencyCode());
        RequestFingerprintEntryFieldWriter.appendField(
            canonical, "callerAuthoredEntry.costMinorUnits", capitalization.cost().minorUnits());
        RequestFingerprintEntryFieldWriter.appendField(
            canonical,
            "callerAuthoredEntry.depreciationSchedule.inServiceDate",
            capitalization.depreciationSchedule().inServiceDate().toString());
        RequestFingerprintEntryFieldWriter.appendField(
            canonical,
            "callerAuthoredEntry.depreciationSchedule.usefulLifeMonths",
            Integer.toString(capitalization.depreciationSchedule().usefulLifeMonths()));
        RequestFingerprintEntryFieldWriter.appendField(
            canonical,
            "callerAuthoredEntry.depreciationSchedule.residualValueCurrency",
            capitalization.depreciationSchedule().residualValue().currencyCode());
        RequestFingerprintEntryFieldWriter.appendField(
            canonical,
            "callerAuthoredEntry.depreciationSchedule.residualValueMinorUnits",
            capitalization.depreciationSchedule().residualValue().minorUnits());
      }
      case FixedAssetBookkeepingEntryVariants.Depreciation depreciation ->
          appendId(canonical, depreciation.fixedAssetId().value());
      case FixedAssetBookkeepingEntryVariants.Disposal disposal -> {
        appendId(canonical, disposal.fixedAssetId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "cashAccountCode", disposal.cashAccountCode());
        RequestFingerprintEntryFieldWriter.appendField(
            canonical, "callerAuthoredEntry.proceedsCurrency", disposal.proceeds().currencyCode());
        RequestFingerprintEntryFieldWriter.appendField(
            canonical, "callerAuthoredEntry.proceedsMinorUnits", disposal.proceeds().minorUnits());
      }
    }
  }

  private static void appendId(StringBuilder canonical, String fixedAssetId) {
    RequestFingerprintEntryFieldWriter.appendField(
        canonical, "callerAuthoredEntry.fixedAssetId", fixedAssetId);
  }
}
