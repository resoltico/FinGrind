package dev.erst.fingrind.contract.bookkeeping;

import java.util.List;

/** Fixed-assets lifecycle violation definitions. */
final class EntrySemanticsFixedAssetViolationDefinitions {
  private EntrySemanticsFixedAssetViolationDefinitions() {}

  static List<EntrySemanticsViolationDefinition> definitions() {
    return List.of(
        definition(
            "fixed-asset-id-already-exists",
            "fixed-asset-lifecycle",
            "The selected fixedAssetId already identifies one durable fixed asset in this book.",
            "Choose a new fixedAssetId for a distinct asset, or use the existing asset's admitted depreciation or disposal command."),
        definition(
            "fixed-asset-not-found",
            "fixed-asset-lifecycle",
            "The selected fixedAssetId does not identify one active fixed asset in this book.",
            "Use a fixedAssetId returned by a prior fixed-asset capitalization posting."),
        definition(
            "fixed-asset-already-disposed",
            "fixed-asset-lifecycle",
            "The selected fixed asset has already been disposed and admits no further depreciation or disposal.",
            "Use a different active fixedAssetId, or correct the prior disposal through its historical reversal."),
        definition(
            "fixed-asset-depreciation-precedes-in-service-date",
            "fixed-asset-ordering",
            "The requested depreciation effective date precedes the fixed asset's declared in-service date.",
            "Use an effectiveDate on or after the fixed asset's inServiceDate."),
        definition(
            "fixed-asset-lifecycle-precedes-horizon",
            "fixed-asset-ordering",
            "The requested fixed-asset lifecycle event precedes the asset's retained lifecycle horizon.",
            "Use an effectiveDate on or after the latest retained depreciation or disposal event."),
        definition(
            "fixed-asset-fully-depreciated",
            "fixed-asset-depreciation",
            "The selected fixed asset has no remaining depreciable carrying amount.",
            "Do not record another depreciation charge; dispose of the asset when its business use ends."),
        definition(
            "fixed-asset-disposal-currency-mismatch",
            "fixed-asset-currency",
            "The disposal proceeds do not use the fixed asset's functional carrying currency.",
            "Use proceeds in the fixed asset's functional carrying currency."));
  }

  private static EntrySemanticsViolationDefinition definition(
      String code, String category, String description, String repair) {
    return new EntrySemanticsViolationDefinition(code, category, description, repair);
  }
}
