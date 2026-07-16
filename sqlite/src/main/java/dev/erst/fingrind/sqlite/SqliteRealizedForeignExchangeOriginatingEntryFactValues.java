package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedRealizedForeignExchangeSettlement;
import org.jspecify.annotations.Nullable;

/**
 * Maps realized-foreign-exchange entry facts to the scalar provenance columns retained with a
 * posting.
 */
final class SqliteRealizedForeignExchangeOriginatingEntryFactValues {
  private SqliteRealizedForeignExchangeOriginatingEntryFactValues() {}

  static SqliteOriginatingEntryFactMapper.OriginatingEntryFactValues originatingEntryFactValues(
      RealizedForeignExchangeBookkeepingEntryVariants entry) {
    return switch (entry) {
      case RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable receivable ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              receivable.receivableAccountCode().value(),
              receivable.revenueAccountCode().value(),
              receivable.foreignExchangeDetails().functionalAmount(),
              null);
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement ->
          settlementFactValues(
              settlement.resolvedSettlement(),
              settlement.cashAccountCode().value(),
              settlement.foreignExchangeDetails().functionalAmount());
    };
  }

  private static SqliteOriginatingEntryFactMapper.OriginatingEntryFactValues settlementFactValues(
      @Nullable ResolvedRealizedForeignExchangeSettlement resolved,
      String cashAccountCode,
      dev.erst.fingrind.contract.bookkeeping.MonetaryAmount functionalSettlementAmount) {
    ResolvedRealizedForeignExchangeSettlement required =
        java.util.Objects.requireNonNull(
            resolved, "realized foreign-exchange settlement requires executor resolution");
    return SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
        cashAccountCode,
        required.receivableAccountCode().value(),
        functionalSettlementAmount,
        null);
  }
}
