package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.CliPostingEntryPayloadComponents.PayloadAccounts;
import dev.erst.fingrind.cli.json.CliFixedAssetPostingJsonModels;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDepreciation;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDisposal;
import org.jspecify.annotations.Nullable;

/** Maps fixed-asset caller facts and executor resolutions into posting payloads. */
final class CliFixedAssetPostingEntryPayloadMapper {
  private CliFixedAssetPostingEntryPayloadMapper() {}

  static CliPostingEntryPayload entryPayload(FixedAssetBookkeepingEntryVariants entry) {
    return switch (entry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization capitalization ->
          CliPostingEntryPayloadComponents.payload(
                  capitalization.entryKind().wireValue(),
                  new PayloadAccounts(
                      capitalization.cashAccountCode().value(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null),
                  null)
              .withFixedAsset(
                  new CliFixedAssetPostingJsonModels.FixedAssetPayload(
                      capitalization.fixedAssetId().value(),
                      "CAPITALIZATION",
                      capitalization.assetAccountCode().value(),
                      capitalization.accumulatedDepreciationAccountCode().value(),
                      capitalization.depreciationExpenseAccountCode().value(),
                      capitalization.disposalGainAccountCode().value(),
                      capitalization.disposalLossAccountCode().value(),
                      capitalization.cost(),
                      new CliFixedAssetPostingJsonModels.FixedAssetDepreciationSchedulePayload(
                          capitalization.depreciationSchedule().inServiceDate().toString(),
                          capitalization.depreciationSchedule().usefulLifeMonths(),
                          capitalization.depreciationSchedule().residualValue()),
                      null,
                      null))
              .build();
      case FixedAssetBookkeepingEntryVariants.Depreciation depreciation ->
          CliPostingEntryPayloadComponents.payload(
                  depreciation.entryKind().wireValue(), PayloadAccounts.none(), null)
              .withFixedAsset(
                  new CliFixedAssetPostingJsonModels.FixedAssetPayload(
                      depreciation.fixedAssetId().value(),
                      "DEPRECIATION",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      resolvedDepreciationPayload(depreciation.resolvedDepreciation()),
                      null))
              .build();
      case FixedAssetBookkeepingEntryVariants.Disposal disposal ->
          CliPostingEntryPayloadComponents.payload(
                  disposal.entryKind().wireValue(),
                  new PayloadAccounts(
                      disposal.cashAccountCode().value(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null),
                  disposal.proceeds())
              .withFixedAsset(
                  new CliFixedAssetPostingJsonModels.FixedAssetPayload(
                      disposal.fixedAssetId().value(),
                      "DISPOSAL",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      resolvedDisposalPayload(disposal.resolvedDisposal())))
              .build();
    };
  }

  private static CliFixedAssetPostingJsonModels.@Nullable ResolvedFixedAssetDepreciationPayload
      resolvedDepreciationPayload(@Nullable ResolvedFixedAssetDepreciation resolvedDepreciation) {
    return resolvedDepreciation == null
        ? null
        : new CliFixedAssetPostingJsonModels.ResolvedFixedAssetDepreciationPayload(
            resolvedDepreciation.depreciationExpenseAccountCode().value(),
            resolvedDepreciation.accumulatedDepreciationAccountCode().value(),
            resolvedDepreciation.amount());
  }

  private static CliFixedAssetPostingJsonModels.@Nullable ResolvedFixedAssetDisposalPayload
      resolvedDisposalPayload(@Nullable ResolvedFixedAssetDisposal resolvedDisposal) {
    return resolvedDisposal == null
        ? null
        : new CliFixedAssetPostingJsonModels.ResolvedFixedAssetDisposalPayload(
            resolvedDisposal.assetAccountCode().value(),
            resolvedDisposal.accumulatedDepreciationAccountCode().value(),
            resolvedDisposal.gainOrLossAccountCode().value(),
            resolvedDisposal.assetCost(),
            resolvedDisposal.accumulatedDepreciation(),
            resolvedDisposal.carryingAmount(),
            resolvedDisposal.gainOrLossAmount(),
            resolvedDisposal.gain());
  }
}
