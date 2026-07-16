package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.executor.bookkeeping.FinancingArrangementRecord;
import dev.erst.fingrind.executor.bookkeeping.FixedAssetRecord;
import dev.erst.fingrind.executor.bookkeeping.ForeignCurrencyObligationRecord;
import java.util.Objects;
import java.util.Optional;

/** Transaction-scoped lifecycle aggregate lookups used while admitting a posting. */
final class SqliteTransactionLifecycleValidationQueries {
  private final SqliteNativeDatabase activeDatabase;

  SqliteTransactionLifecycleValidationQueries(SqliteNativeDatabase activeDatabase) {
    this.activeDatabase = Objects.requireNonNull(activeDatabase, "activeDatabase");
  }

  Optional<FixedAssetRecord> findFixedAsset(FixedAssetId fixedAssetId) {
    try {
      return SqliteFixedAssetStatementQueries.load(activeDatabase, Optional.empty()).stream()
          .filter(asset -> asset.fixedAssetId().equals(fixedAssetId))
          .findFirst();
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite fixed asset.", exception);
    }
  }

  boolean hasFixedAsset(FixedAssetId fixedAssetId) {
    try {
      return SqliteFixedAssetStatementQueries.exists(activeDatabase, fixedAssetId);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite fixed-asset history.", exception);
    }
  }

  Optional<FinancingArrangementRecord> findFinancingArrangement(
      FinancingArrangementId financingArrangementId) {
    try {
      return SqliteFinancingStatementQueries.find(activeDatabase, financingArrangementId);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite financing arrangement.", exception);
    }
  }

  boolean hasFinancingArrangement(FinancingArrangementId financingArrangementId) {
    try {
      return SqliteFinancingStatementQueries.exists(activeDatabase, financingArrangementId);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite financing arrangement history.", exception);
    }
  }

  Optional<ForeignCurrencyObligationRecord> findForeignCurrencyObligation(
      ForeignCurrencyObligationId foreignCurrencyObligationId) {
    try {
      return SqliteRealizedForeignExchangeStatementQueries.find(
          activeDatabase, foreignCurrencyObligationId);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite foreign-currency obligation.", exception);
    }
  }

  boolean hasForeignCurrencyObligation(ForeignCurrencyObligationId foreignCurrencyObligationId) {
    try {
      return SqliteRealizedForeignExchangeStatementQueries.exists(
          activeDatabase, foreignCurrencyObligationId);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite foreign-currency obligation history.", exception);
    }
  }
}
