package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.executor.bookkeeping.ForeignCurrencyObligationRecord;
import dev.erst.fingrind.executor.spi.RealizedForeignExchangeLookupStore;
import java.util.Optional;

/** Realized foreign-exchange aggregate lookup defaults for SQLite read wrappers. */
interface SqliteReadRealizedForeignExchangeCapabilityView
    extends RealizedForeignExchangeLookupStore, SqlitePostingFactStoreReadOperationsView {
  @Override
  default Optional<ForeignCurrencyObligationRecord> findForeignCurrencyObligation(
      ForeignCurrencyObligationId foreignCurrencyObligationId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations()
        .realizedForeignExchange()
        .findForeignCurrencyObligation(foreignCurrencyObligationId);
  }

  @Override
  default boolean hasForeignCurrencyObligation(
      ForeignCurrencyObligationId foreignCurrencyObligationId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations()
        .realizedForeignExchange()
        .hasForeignCurrencyObligation(foreignCurrencyObligationId);
  }

  @Override
  default java.util.List<ForeignCurrencyObligationRecord> foreignCurrencyObligations() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().realizedForeignExchange().foreignCurrencyObligations();
  }
}
