package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.executor.bookkeeping.FinancingArrangementRecord;
import dev.erst.fingrind.executor.bookkeeping.FixedAssetRecord;
import dev.erst.fingrind.executor.bookkeeping.ForeignCurrencyObligationRecord;
import dev.erst.fingrind.executor.spi.FinancingLookupStore;
import dev.erst.fingrind.executor.spi.FixedAssetLookupStore;
import dev.erst.fingrind.executor.spi.RealizedForeignExchangeLookupStore;
import java.util.Optional;

/** Resolves owned lifecycle aggregates inside the SQLite transaction that admits a posting. */
interface SqliteTransactionValidationOwnedContextCapabilityView
    extends FixedAssetLookupStore, FinancingLookupStore, RealizedForeignExchangeLookupStore {
  @Override
  default Optional<FixedAssetRecord> findFixedAsset(FixedAssetId fixedAssetId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .lifecycleContextQueries()
        .findFixedAsset(fixedAssetId);
  }

  @Override
  default boolean hasFixedAsset(FixedAssetId fixedAssetId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .lifecycleContextQueries()
        .hasFixedAsset(fixedAssetId);
  }

  @Override
  default Optional<FinancingArrangementRecord> findFinancingArrangement(
      FinancingArrangementId financingArrangementId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .lifecycleContextQueries()
        .findFinancingArrangement(financingArrangementId);
  }

  @Override
  default boolean hasFinancingArrangement(FinancingArrangementId financingArrangementId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .lifecycleContextQueries()
        .hasFinancingArrangement(financingArrangementId);
  }

  @Override
  default Optional<ForeignCurrencyObligationRecord> findForeignCurrencyObligation(
      ForeignCurrencyObligationId foreignCurrencyObligationId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .lifecycleContextQueries()
        .findForeignCurrencyObligation(foreignCurrencyObligationId);
  }

  @Override
  default boolean hasForeignCurrencyObligation(
      ForeignCurrencyObligationId foreignCurrencyObligationId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .lifecycleContextQueries()
        .hasForeignCurrencyObligation(foreignCurrencyObligationId);
  }
}
