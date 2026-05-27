package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;

/** Staged encrypted backup pair that is either published atomically or discarded. */
public interface StagedBackupPair extends AutoCloseable {
  /** Verifies that the staged backup file already opens as one initialized protected book. */
  MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification> verifyInitializedBackup();

  /** Publishes the staged backup pair to its final destinations. */
  void commit();

  /** Discards the staged backup pair without publishing it. */
  void rollback();

  @Override
  void close();
}
