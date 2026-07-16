package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import dev.erst.fingrind.executor.spi.AccrualCutoffLookupStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Accrual-cutoff lifecycle defaults for SQLite read wrappers. */
interface SqliteReadAccrualCutoffCapabilityView
    extends AccrualCutoffLookupStore, SqlitePostingFactStoreReadOperationsView {
  @Override
  default Optional<AccrualCutoffRecord> findAccrualCutoff(AccrualCutoffId accrualCutoffId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accrualCutoffLifecycle().findAccrualCutoff(accrualCutoffId);
  }

  @Override
  default List<AccrualCutoffRecord> accrualCutoffs(Optional<LocalDate> effectiveDateAsOf) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accrualCutoffLifecycle().accrualCutoffs(effectiveDateAsOf);
  }
}
