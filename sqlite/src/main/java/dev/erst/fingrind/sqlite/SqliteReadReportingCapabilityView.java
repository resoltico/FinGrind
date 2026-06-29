package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import java.util.List;

/** Shared reporting and balance defaults for SQLite read wrappers. */
interface SqliteReadReportingCapabilityView
    extends SqliteLifecycleInspectionCapabilityView, SqliteReportingReadOperationsView {

  @Override
  default List<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> postings(
      EffectiveDateRange effectiveDateRange) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().reporting().postings(effectiveDateRange);
  }
}
