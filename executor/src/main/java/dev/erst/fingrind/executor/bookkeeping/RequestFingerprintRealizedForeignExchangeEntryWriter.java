package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;

/** Writes canonical caller-authored fingerprint fields for realized-foreign-exchange entries. */
final class RequestFingerprintRealizedForeignExchangeEntryWriter {
  private RequestFingerprintRealizedForeignExchangeEntryWriter() {}

  static void append(
      StringBuilder canonical, RealizedForeignExchangeBookkeepingEntryVariants entry) {
    switch (entry) {
      case RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable receivable -> {
        appendId(canonical, receivable.foreignCurrencyObligationId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "receivableAccountCode", receivable.receivableAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "revenueAccountCode", receivable.revenueAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "realizedGainAccountCode", receivable.realizedGainAccountCode());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "realizedLossAccountCode", receivable.realizedLossAccountCode());
        RequestFingerprintEntryFieldWriter.appendOptionalForeignExchangeDetails(
            canonical, receivable.foreignExchangeDetails());
      }
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement -> {
        appendId(canonical, settlement.foreignCurrencyObligationId().value());
        RequestFingerprintEntryFieldWriter.appendAccountCode(
            canonical, "cashAccountCode", settlement.cashAccountCode());
        RequestFingerprintEntryFieldWriter.appendOptionalForeignExchangeDetails(
            canonical, settlement.foreignExchangeDetails());
      }
    }
  }

  private static void appendId(StringBuilder canonical, String foreignCurrencyObligationId) {
    RequestFingerprintEntryFieldWriter.appendField(
        canonical, "callerAuthoredEntry.foreignCurrencyObligationId", foreignCurrencyObligationId);
  }
}
