package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;

/**
 * Staged encrypted backup pair whose private artifacts are owned only by its transaction journal.
 */
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

  /**
   * Publishes the sealed staged backup pair to its final destinations.
   *
   * <p>An incomplete result names the journal-owned transaction that must be recovered before the
   * final pair can be used. It never authorizes callers to inspect or manipulate private stages.
   */
  StagedPairPublicationCommitOutcome commit();

  /** Relinquishes this workflow's in-process access while the journal retains private stages. */
  void retainUnpublishedArtifacts();

  @Override
  void close();
}
