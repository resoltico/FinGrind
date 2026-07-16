package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingOriginKind;
import java.util.ArrayList;
import java.util.List;

/** Entry-surface behavior owned by realized-foreign-exchange bookkeeping-entry variants. */
final class RealizedForeignExchangeBookkeepingEntrySurfaceSupport {
  private RealizedForeignExchangeBookkeepingEntrySurfaceSupport() {}

  static BookkeepingEntryKind entryKind(RealizedForeignExchangeBookkeepingEntryVariants entry) {
    return switch (entry) {
      case RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable _ ->
          BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION;
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement _ ->
          BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT;
    };
  }

  static PostingOriginKind postingOriginKind(
      RealizedForeignExchangeBookkeepingEntryVariants entry) {
    return switch (entry) {
      case RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable _ ->
          PostingOriginKind.FOREIGN_CURRENCY_OBLIGATION;
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement _ ->
          PostingOriginKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT;
    };
  }

  static JournalEntry journalEntry(RealizedForeignExchangeBookkeepingEntryVariants entry) {
    return switch (entry) {
      case RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable receivable ->
          BookkeepingEntrySupport.pairedEntry(
              receivable.effectiveDate(),
              receivable.receivableAccountCode(),
              receivable.revenueAccountCode(),
              receivable.foreignExchangeDetails().functionalAmount());
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement ->
          settlementJournal(settlement);
    };
  }

  private static JournalEntry settlementJournal(
      RealizedForeignExchangeBookkeepingEntryVariants.Settlement entry) {
    ResolvedRealizedForeignExchangeSettlement resolved = entry.resolvedSettlement();
    if (resolved == null) {
      throw new IllegalStateException(
          "realizedForeignExchangeSettlement requires executor-resolved facts.");
    }
    long cash = entry.foreignExchangeDetails().functionalAmount().toMoney().minorUnits();
    long carrying = resolved.carryingAmount().toMoney().minorUnits();
    long difference = Math.abs(cash - carrying);
    if (difference != resolved.realizedGainOrLossAmount().toMoney().minorUnits()) {
      throw new IllegalStateException(
          "Realized foreign-exchange settlement resolution does not reconcile.");
    }
    List<JournalLine> lines = new ArrayList<>();
    lines.add(
        new JournalLine(
            entry.cashAccountCode(),
            JournalLine.EntrySide.DEBIT,
            entry.foreignExchangeDetails().functionalAmount().toMoney()));
    if (!resolved.gain() && difference > 0) {
      lines.add(
          new JournalLine(
              resolved.gainOrLossAccountCode(),
              JournalLine.EntrySide.DEBIT,
              resolved.realizedGainOrLossAmount().toMoney()));
    }
    lines.add(
        new JournalLine(
            resolved.receivableAccountCode(),
            JournalLine.EntrySide.CREDIT,
            resolved.carryingAmount().toMoney()));
    if (resolved.gain() && difference > 0) {
      lines.add(
          new JournalLine(
              resolved.gainOrLossAccountCode(),
              JournalLine.EntrySide.CREDIT,
              resolved.realizedGainOrLossAmount().toMoney()));
    }
    return new JournalEntry(entry.effectiveDate(), lines);
  }
}
