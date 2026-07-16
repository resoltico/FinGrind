package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import dev.erst.fingrind.executor.bookkeeping.FinancingArrangementRecord;
import dev.erst.fingrind.executor.bookkeeping.FixedAssetRecord;
import dev.erst.fingrind.executor.bookkeeping.ForeignCurrencyObligationRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Durable aggregate reads owned by lifecycle business contexts. */
final class SqliteStoreLifecycleQueryOperations {
  private final SqliteStoreQueryOperations queryOperations;

  SqliteStoreLifecycleQueryOperations(SqliteStoreQueryOperations queryOperations) {
    this.queryOperations = Objects.requireNonNull(queryOperations, "queryOperations");
  }

  Optional<AccrualCutoffRecord> findAccrualCutoff(AccrualCutoffId accrualCutoffId) {
    return queryOperations.queryInitialized(
        "Failed to query SQLite accrual cut-off.",
        activeDatabase ->
            SqliteAccrualCutoffStatementQueries.findCutoff(activeDatabase, accrualCutoffId));
  }

  List<AccrualCutoffRecord> accrualCutoffs(Optional<LocalDate> effectiveDateAsOf) {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    return queryOperations.queryInitialized(
        "Failed to query SQLite accrual cut-offs.",
        activeDatabase ->
            SqliteAccrualCutoffStatementQueries.loadCutoffs(activeDatabase, effectiveDateAsOf));
  }

  List<FixedAssetRecord> fixedAssets(Optional<LocalDate> effectiveDateAsOf) {
    return queryOperations.queryInitialized(
        "Failed to query SQLite fixed assets.",
        activeDatabase -> SqliteFixedAssetStatementQueries.load(activeDatabase, effectiveDateAsOf));
  }

  boolean hasFixedAsset(FixedAssetId fixedAssetId) {
    return queryOperations.queryInitialized(
        "Failed to query SQLite fixed-asset history.",
        activeDatabase -> SqliteFixedAssetStatementQueries.exists(activeDatabase, fixedAssetId));
  }

  Optional<FinancingArrangementRecord> findFinancingArrangement(
      FinancingArrangementId financingArrangementId) {
    return queryOperations.queryInitialized(
        "Failed to query SQLite financing arrangement.",
        activeDatabase ->
            SqliteFinancingStatementQueries.find(activeDatabase, financingArrangementId));
  }

  boolean hasFinancingArrangement(FinancingArrangementId financingArrangementId) {
    return queryOperations.queryInitialized(
        "Failed to query SQLite financing arrangement history.",
        activeDatabase ->
            SqliteFinancingStatementQueries.exists(activeDatabase, financingArrangementId));
  }

  List<FinancingArrangementRecord> financingArrangements() {
    return queryOperations.queryInitialized(
        "Failed to query SQLite financing arrangements.", SqliteFinancingStatementQueries::load);
  }

  Optional<ForeignCurrencyObligationRecord> findForeignCurrencyObligation(
      ForeignCurrencyObligationId foreignCurrencyObligationId) {
    return queryOperations.queryInitialized(
        "Failed to query SQLite foreign-currency obligation.",
        activeDatabase ->
            SqliteRealizedForeignExchangeStatementQueries.find(
                activeDatabase, foreignCurrencyObligationId));
  }

  boolean hasForeignCurrencyObligation(ForeignCurrencyObligationId foreignCurrencyObligationId) {
    return queryOperations.queryInitialized(
        "Failed to query SQLite foreign-currency obligation history.",
        activeDatabase ->
            SqliteRealizedForeignExchangeStatementQueries.exists(
                activeDatabase, foreignCurrencyObligationId));
  }

  List<ForeignCurrencyObligationRecord> foreignCurrencyObligations() {
    return queryOperations.queryInitialized(
        "Failed to query SQLite foreign-currency obligations.",
        SqliteRealizedForeignExchangeStatementQueries::load);
  }
}
