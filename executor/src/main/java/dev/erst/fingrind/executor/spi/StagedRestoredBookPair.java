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
   * <p>An incomplete result names the journal-owned transaction that must be recovered before the
   * final pair can be used. It never authorizes callers to inspect or manipulate private stages.
   */
  StagedPairPublicationCommitOutcome commit();

  /** Relinquishes this workflow's in-process access while the journal retains private stages. */
  void retainUnpublishedArtifacts();

  @Override
  void close();
}
