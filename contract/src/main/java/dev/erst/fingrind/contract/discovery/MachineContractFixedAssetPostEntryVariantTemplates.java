package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractFixedAssetTemplates.FixedAssetDepreciationScheduleTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractFixedAssetTemplates.FixedAssetTemplateDescriptor;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.jspecify.annotations.Nullable;

/** Request scaffolds owned by the Fixed Assets context. */
final class MachineContractFixedAssetPostEntryVariantTemplates {
  private static final String SAMPLE_EFFECTIVE_DATE = "2026-01-15";
  private static final String SAMPLE_FIXED_ASSET_ID = "delivery-van-001";

  private MachineContractFixedAssetPostEntryVariantTemplates() {}

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor capitalizationTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return template(
        BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION,
        "cash",
        new FixedAssetTemplateDescriptor(
            SAMPLE_FIXED_ASSET_ID,
            "delivery-van",
            "delivery-van-accumulated-depreciation",
            "depreciation-expense",
            "fixed-asset-disposal-gain",
            "fixed-asset-disposal-loss",
            new MonetaryAmount("EUR", "1200000"),
            new FixedAssetDepreciationScheduleTemplateDescriptor(
                SAMPLE_EFFECTIVE_DATE, 60, new MonetaryAmount("EUR", "200000")),
            null));
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor depreciationTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return template(
        BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION,
        null,
        new FixedAssetTemplateDescriptor(
            SAMPLE_FIXED_ASSET_ID, null, null, null, null, null, null, null, null));
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor disposalTemplate(
      @Nullable BookTemplateId ignoredBookTemplateId) {
    return template(
        BookkeepingEntryKind.FIXED_ASSET_DISPOSAL,
        "cash",
        new FixedAssetTemplateDescriptor(
            SAMPLE_FIXED_ASSET_ID,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new MonetaryAmount("EUR", "800000")));
  }

  private static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor template(
      BookkeepingEntryKind entryKind,
      @Nullable String cashAccountCode,
      FixedAssetTemplateDescriptor fixedAsset) {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        SAMPLE_EFFECTIVE_DATE,
        cashAccountCode,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        MachineContractPostEntryVariantTemplates.evidenceTemplate(entryKind),
        MachineContractPostEntryVariantTemplates.provenanceTemplate(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        fixedAsset,
        null,
        null);
  }
}
