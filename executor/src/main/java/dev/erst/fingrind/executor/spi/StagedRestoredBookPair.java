package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;

/** Staged restored live-book pair that publishes one re-encrypted book and key file together. */
public interface StagedRestoredBookPair extends AutoCloseable {
  /** Verifies that the staged restored book already opens with the staged destination key file. */
  MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
      verifyInitializedRestoredBook();

  /**
   * Publishes the staged restored book and staged destination key file.
   *
   * <p>A completion-uncertain result means the final book member was attempted. Callers must
   * preserve both final paths and use protected-book recovery before any retry.
   */
  StagedPairPublicationCommitOutcome commit(ProtectedBookPairPublicationBinding binding);

  /** Relinquishes this workflow's authority while retaining unpublished pair artifacts. */
  void retainUnpublishedArtifacts();

  @Override
  void close();
}
