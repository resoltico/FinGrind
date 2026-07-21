package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;

/** Staged encrypted backup pair that is either published atomically or discarded. */
public interface StagedBackupPair extends AutoCloseable {
  /** Verifies that the staged backup file already opens as one initialized protected book. */
  MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification> verifyInitializedBackup();

  /** Returns the exact opaque encrypted SQLite snapshot before it is wrapped in its manifest. */
  byte[] snapshot();

  /**
   * Replaces the staged raw snapshot with its independently verified manifest-attested container.
   *
   * <p>The container must begin with precisely {@link #snapshot()}'s bytes and remain unpublished
   * until {@link #commit()} performs the no-clobber publication.
   */
  void sealArtifact(byte[] artifact);

  /** Publishes the staged backup pair to its final destinations. */
  void commit();

  /** Discards the staged backup pair without publishing it. */
  void rollback();

  @Override
  void close();
}
