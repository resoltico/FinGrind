package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;

/**
 * Staged encrypted backup pair whose private artifacts remain retained after this workflow ends.
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
   * until {@link #commit(ProtectedBookPairPublicationBinding)} performs the no-clobber publication.
   */
  void sealArtifact(byte[] artifact);

  /**
   * Publishes the sealed staged backup pair to its final destinations.
   *
   * <p>A completion-uncertain result means the final backup member was attempted. Callers must
   * preserve both final paths and retry through backup recovery rather than treating the
   * unpublished stages as disposable.
   */
  StagedPairPublicationCommitOutcome commit(ProtectedBookPairPublicationBinding binding);

  /** Relinquishes this workflow's authority while retaining unpublished pair artifacts. */
  void retainUnpublishedArtifacts();

  @Override
  void close();
}
