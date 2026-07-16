package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingOriginKind;
import java.util.List;

/** Entry-surface behavior owned by fixed-asset bookkeeping-entry variants. */
final class FixedAssetBookkeepingEntrySurfaceSupport {
  private FixedAssetBookkeepingEntrySurfaceSupport() {}

  static BookkeepingEntryKind entryKind(FixedAssetBookkeepingEntryVariants entry) {
    return switch (entry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization _ ->
          BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION;
      case FixedAssetBookkeepingEntryVariants.Depreciation _ ->
          BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION;
      case FixedAssetBookkeepingEntryVariants.Disposal _ ->
          BookkeepingEntryKind.FIXED_ASSET_DISPOSAL;
    };
  }

  static PostingOriginKind postingOriginKind(FixedAssetBookkeepingEntryVariants entry) {
    return switch (entry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization _ ->
          PostingOriginKind.FIXED_ASSET_CAPITALIZATION;
      case FixedAssetBookkeepingEntryVariants.Depreciation _ ->
          PostingOriginKind.FIXED_ASSET_DEPRECIATION;
      case FixedAssetBookkeepingEntryVariants.Disposal _ -> PostingOriginKind.FIXED_ASSET_DISPOSAL;
    };
  }

  static JournalEntry journalEntry(FixedAssetBookkeepingEntryVariants entry) {
    return switch (entry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization capitalization ->
          BookkeepingEntrySupport.pairedEntry(
              capitalization.effectiveDate(),
              capitalization.assetAccountCode(),
              capitalization.cashAccountCode(),
              capitalization.cost());
      case FixedAssetBookkeepingEntryVariants.Depreciation depreciation ->
          depreciationJournal(depreciation);
      case FixedAssetBookkeepingEntryVariants.Disposal disposal -> disposalJournal(disposal);
    };
  }

  private static JournalEntry depreciationJournal(
      FixedAssetBookkeepingEntryVariants.Depreciation entry) {
    ResolvedFixedAssetDepreciation resolved =
        java.util.Objects.requireNonNull(
            entry.resolvedDepreciation(), "fixed-asset depreciation requires executor resolution");
    return BookkeepingEntrySupport.pairedEntry(
        entry.effectiveDate(),
        resolved.depreciationExpenseAccountCode(),
        resolved.accumulatedDepreciationAccountCode(),
        resolved.amount());
  }

  private static JournalEntry disposalJournal(FixedAssetBookkeepingEntryVariants.Disposal entry) {
    ResolvedFixedAssetDisposal resolved =
        java.util.Objects.requireNonNull(
            entry.resolvedDisposal(), "fixed-asset disposal requires executor resolution");
    long proceeds = entry.proceeds().toMoney().minorUnits();
    long carrying = resolved.carryingAmount().toMoney().minorUnits();
    long difference = Math.abs(proceeds - carrying);
    if (difference != resolved.gainOrLossAmount().toMoney().minorUnits()) {
      throw new IllegalStateException(
          "Fixed-asset disposal resolution does not reconcile to proceeds.");
    }
    List<JournalLine> lines = new java.util.ArrayList<>();
    if (proceeds > 0) {
      lines.add(
          new JournalLine(
              entry.cashAccountCode(), JournalLine.EntrySide.DEBIT, entry.proceeds().toMoney()));
    }
    lines.add(
        new JournalLine(
            resolved.accumulatedDepreciationAccountCode(),
            JournalLine.EntrySide.DEBIT,
            resolved.accumulatedDepreciation().toMoney()));
    if (resolved.gain() && difference > 0) {
      lines.add(
          new JournalLine(
              resolved.gainOrLossAccountCode(),
              JournalLine.EntrySide.CREDIT,
              resolved.gainOrLossAmount().toMoney()));
    }
    lines.add(
        new JournalLine(
            resolved.assetAccountCode(),
            JournalLine.EntrySide.CREDIT,
            resolved.assetCost().toMoney()));
    if (!resolved.gain() && difference > 0) {
      lines.add(
          new JournalLine(
              resolved.gainOrLossAccountCode(),
              JournalLine.EntrySide.DEBIT,
              resolved.gainOrLossAmount().toMoney()));
    }
    return new JournalEntry(entry.effectiveDate(), lines);
  }
}
