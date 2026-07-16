package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Staged restored live-book pair that publishes one re-encrypted book and key file together. */
final class SqliteStagedRestoredBookPair implements StagedRestoredBookPair {
  private final SqliteOwnedStagedArtifact stagedBookFile;
  private final SqliteOwnedStagedArtifact stagedBookKeyFile;
  private final SqliteProtectedBookVerificationSupport verificationSupport;
  private final SqliteRestoredBookPairPublication publication;
  private @Nullable SqliteBookPassphrase restoredPassphrase;
  private boolean bookKeyFilePublished;
  private boolean finished;

  SqliteStagedRestoredBookPair(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      byte[] restoredPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteRestoredBookPairPublication publication) {
    SqliteStagedProtectedBookPairArtifacts checkedArtifacts =
        Objects.requireNonNull(artifacts, "artifacts");
    this.stagedBookFile = checkedArtifacts.stagedBookFile();
    this.stagedBookKeyFile = checkedArtifacts.stagedSecretFile();
    this.restoredPassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "staged restored-book passphrase",
            Objects.requireNonNull(restoredPassphraseBytes, "restoredPassphraseBytes"));
    this.verificationSupport = Objects.requireNonNull(verificationSupport, "verificationSupport");
    this.publication = Objects.requireNonNull(publication, "publication");
  }

  @Override
  public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
      verifyInitializedRestoredBook() {
    return MaintenanceDecision.accepted(
        verificationSupport.verifyResolvedBook(
            stagedBookFile.stagedPath(), currentRestoredPassphrase().copy()));
  }

  @Override
  public void commit() {
    if (finished) {
      return;
    }
    try {
      stagedBookFile.requireIntactFor(publication.bookTargetPath());
      stagedBookKeyFile.requireIntactFor(publication.secretTargetPath());
      publication.publishSecret(stagedBookKeyFile);
      bookKeyFilePublished = true;
      publication.publishBook(stagedBookFile);
      closeUnusedPassphrase();
    } catch (SqliteGeneratedSecretTargetOccupiedException exception) {
      try {
        finishAfterFailedPublication();
      } finally {
        closeUnusedPassphrase();
      }
      throw new ProtectedBookMaintenanceRejectionException(
          new ProtectedBookMaintenanceRejection.SecretTargetOccupied(exception.targetPath()),
          exception);
    } catch (SqliteCallerPathContractException exception) {
      try {
        finishAfterFailedPublication();
      } finally {
        closeUnusedPassphrase();
      }
      throw new ProtectedBookMaintenanceRejectionException(
          SqliteCallerPathFailureMapper.maintenanceRejection(
              dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole
                  .RESTORED_TARGET,
              exception),
          exception);
    } catch (java.nio.file.FileAlreadyExistsException exception) {
      try {
        finishAfterFailedPublication();
      } finally {
        closeUnusedPassphrase();
      }
      throw new ProtectedBookMaintenanceRejectionException(
          new ProtectedBookMaintenanceRejection.BookDestinationOccupied(
              publication.bookTargetPath()),
          exception);
    } catch (IOException exception) {
      try {
        finishAfterFailedPublication();
      } finally {
        closeUnusedPassphrase();
      }
      throw new IllegalStateException(
          "Failed to publish the restored FinGrind live-book pair at "
              + SqliteMachinePaths.absoluteValue(publication.bookTargetPath())
              + ".",
          exception);
    } catch (RuntimeException exception) {
      try {
        finishAfterFailedPublication();
      } finally {
        closeUnusedPassphrase();
      }
      throw exception;
    }
    finishAfterSuccessfulPublication();
  }

  @Override
  public void rollback() {
    if (finished) {
      return;
    }
    try {
      rollbackInterruptedPair();
    } finally {
      closeUnusedPassphrase();
      finished = true;
    }
  }

  @Override
  public void close() {
    if (!finished) {
      rollback();
    }
  }

  private void rollbackInterruptedPair() {
    if (bookKeyFilePublished) {
      SqliteProtectedBookPublicationRecovery.removePublishedSecretIfOwned(
          publication.secretTargetPath(),
          stagedBookKeyFile,
          "rolling back one interrupted generated restored-book key publication");
    }
    try {
      stagedBookFile.discard();
    } finally {
      try {
        stagedBookKeyFile.discard();
      } finally {
        closeReservations();
      }
    }
  }

  private void finishAfterSuccessfulPublication() {
    try {
      discardCommittedStages();
    } catch (RuntimeException cleanupFailure) {
      // A restored or rekeyed pair is committed at publication and cannot safely be rolled back
      // here.
      SqliteBestEffort.reportCleanupFailure(
          "discarding owned stages after protected-book pair publication", cleanupFailure);
    } finally {
      finished = true;
    }
  }

  private void discardCommittedStages() {
    try {
      stagedBookFile.discard();
    } finally {
      try {
        stagedBookKeyFile.discard();
      } finally {
        closeReservations();
      }
    }
  }

  private void finishAfterFailedPublication() {
    try {
      rollbackInterruptedPair();
    } catch (RuntimeException cleanupFailure) {
      finished = true;
      throw new IllegalStateException(
          "Failed to roll back the staged FinGrind restored-book pair; durable owned stages remain for recovery.",
          cleanupFailure);
    }
    finished = true;
  }

  private SqliteBookPassphrase currentRestoredPassphrase() {
    return Objects.requireNonNull(restoredPassphrase, "restoredPassphrase");
  }

  private void closeUnusedPassphrase() {
    if (restoredPassphrase != null) {
      restoredPassphrase.close();
      restoredPassphrase = null;
    }
  }

  private void closeReservations() {
    publication.closeReservations();
  }
}
