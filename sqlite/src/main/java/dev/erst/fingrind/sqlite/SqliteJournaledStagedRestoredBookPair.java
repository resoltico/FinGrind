package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.core.PublicationTransactionExecutionException;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Staged restored-book pair whose final publication is exclusively owned by one transaction. */
final class SqliteJournaledStagedRestoredBookPair implements StagedRestoredBookPair {
  private final SqlitePublicationTransactionPair publication;
  private final Path bookStagePath;
  private final SqliteBookPassphrase restoredPassphrase;
  private final SqliteProtectedBookVerificationSupport verificationSupport;
  private @org.jspecify.annotations.Nullable StagedPairPublicationCommitOutcome commitOutcome;
  private boolean closed;

  SqliteJournaledStagedRestoredBookPair(
      SqlitePublicationTransactionPair publication,
      Path bookStagePath,
      SqliteBookPassphrase restoredPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    this.publication = Objects.requireNonNull(publication, "publication");
    this.bookStagePath = Objects.requireNonNull(bookStagePath, "bookStagePath");
    this.restoredPassphrase = Objects.requireNonNull(restoredPassphrase, "restoredPassphrase");
    this.verificationSupport = Objects.requireNonNull(verificationSupport, "verificationSupport");
  }

  @Override
  public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
      verifyInitializedRestoredBook() {
    requireOpen();
    return MaintenanceDecision.accepted(
        verificationSupport.verifyResolvedBook(bookStagePath, restoredPassphrase.copy()));
  }

  @Override
  public StagedPairPublicationCommitOutcome commit() {
    if (commitOutcome != null) {
      return commitOutcome;
    }
    requireOpen();
    try {
      ProtectedBookPairPublication completed = publication.publish();
      close();
      commitOutcome = new StagedPairPublicationCommitOutcome.Published(completed);
      return commitOutcome;
    } catch (PublicationTransactionExecutionException incomplete) {
      close();
      commitOutcome =
          new StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete(
              publication.bookTargetPath(), incomplete.result());
      return commitOutcome;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to publish the journal-owned restored protected-book pair.", exception);
    }
  }

  @Override
  public void retainUnpublishedArtifacts() {
    close();
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      try {
        restoredPassphrase.close();
      } finally {
        publication.releaseStageAccess();
      }
    }
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("The journal-owned restored protected-book pair is closed.");
    }
  }
}
