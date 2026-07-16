package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Set;

/** Request facts owned by the fixed-assets context. */
final class FixedAssetRequestSurfaceContracts {
  private FixedAssetRequestSurfaceContracts() {}

  static List<RequestSurfaceFacts.BookkeepingEntryKindFacts> entryKindFacts() {
    return List.of(
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION,
            ProtocolFixedAssetPostingRequestFieldSets.capitalizationFields(),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("supplier-invoice", "purchase-agreement", "asset-receipt"),
                "Accepted source-document types for fixed-asset capitalization requests.",
                "supplier-invoice"),
            "Fixed-asset capitalization creates one identifiable asset with a straight-line depreciation schedule, debits its non-current asset account, and credits one cash-and-cash-equivalent asset account."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION,
            ProtocolFixedAssetPostingRequestFieldSets.depreciationFields(),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("depreciation-schedule", "asset-register"),
                "Accepted source-document types for fixed-asset depreciation requests.",
                "depreciation-schedule"),
            "Fixed-asset depreciation charges the next executor-derived straight-line amount for one admitted asset, debiting depreciation expense and crediting accumulated depreciation."),
        RequestSurfaceContracts.entryKindFacts(
            BookkeepingEntryKind.FIXED_ASSET_DISPOSAL,
            ProtocolFixedAssetPostingRequestFieldSets.disposalFields(),
            Set.of(),
            RequestSurfaceContracts.sourceDocumentTypes(
                SourceDocumentTypePolicyMode.ENUMERATED,
                List.of("asset-disposal-agreement", "cash-receipt", "bank-deposit"),
                "Accepted source-document types for fixed-asset disposal requests.",
                "asset-disposal-agreement"),
            "Fixed-asset disposal removes the asset cost and accumulated depreciation, records cash proceeds, and lets FinGrind derive the gain or loss from the retained carrying amount."));
  }
}
