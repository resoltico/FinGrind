package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.executor.bookkeeping.FinancingArrangementRecord;
import dev.erst.fingrind.executor.spi.FinancingLookupStore;
import java.util.Optional;

/** Financing aggregate lookup defaults for SQLite read wrappers. */
interface SqliteReadFinancingCapabilityView
    extends FinancingLookupStore, SqlitePostingFactStoreReadOperationsView {
  @Override
  default Optional<FinancingArrangementRecord> findFinancingArrangement(
      FinancingArrangementId financingArrangementId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().financing().findFinancingArrangement(financingArrangementId);
  }

  @Override
  default boolean hasFinancingArrangement(FinancingArrangementId financingArrangementId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().financing().hasFinancingArrangement(financingArrangementId);
  }

  @Override
  default java.util.List<FinancingArrangementRecord> financingArrangements() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().financing().financingArrangements();
  }
}
