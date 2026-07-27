package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns the terminal evidence, reservation, and secret lifecycle of one staged pair publication. */
final class SqliteStagedPairPublicationFinalizer {
  private final Path bookTargetPath;
  private final Path secretTargetPath;
  private final SqliteOwnedStagedArtifact stagedBookFile;
  private final SqliteOwnedStagedArtifact stagedSecretFile;
  private final Runnable closeUnusedPassphrase;
  private final Runnable closeReservations;
  private final SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer;
  private final String publicationDescription;
  private @Nullable SqliteProtectedBookPairPublicationRecord recoveryRecord;
  private @Nullable StagedPairPublicationCommitOutcome commitOutcome;
  private boolean recoveryBoundaryMayExist;
  private boolean finished;

  SqliteStagedPairPublicationFinalizer(
      Path bookTargetPath,
      Path secretTargetPath,
      SqliteOwnedStagedArtifact stagedBookFile,
      SqliteOwnedStagedArtifact stagedSecretFile,
      Runnable closeUnusedPassphrase,
      Runnable closeReservations,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      String publicationDescription) {
    this.bookTargetPath = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    this.secretTargetPath = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    this.stagedBookFile = Objects.requireNonNull(stagedBookFile, "stagedBookFile");
    this.stagedSecretFile = Objects.requireNonNull(stagedSecretFile, "stagedSecretFile");
    this.closeUnusedPassphrase =
        Objects.requireNonNull(closeUnusedPassphrase, "closeUnusedPassphrase");
    this.closeReservations = Objects.requireNonNull(closeReservations, "closeReservations");
    this.directoryForcer = Objects.requireNonNull(directoryForcer, "directoryForcer");
    this.publicationDescription =
        Objects.requireNonNull(publicationDescription, "publicationDescription");
  }

  @Nullable StagedPairPublicationCommitOutcome cachedOutcome() {
    return commitOutcome;
  }

  boolean isFinished() {
    return finished;
  }

  void recordRecoveryBoundary(SqliteProtectedBookPairPublicationRecord record) {
    recoveryBoundaryMayExist = true;
    recoveryRecord = Objects.requireNonNull(record, "record");
  }

  StagedPairPublicationCommitOutcome finishCompletionUncertain(
      ProtectedBookPairPublicationMemberState bookState,
      ProtectedBookPairPublicationMemberState secretState) {
    closeReservationsBestEffort("completion-uncertain");
    commitOutcome =
        new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
            bookTargetPath, bookState, secretTargetPath, secretState, retainedPublicationStages());
    return commitOutcome;
  }

  StagedPairPublicationCommitOutcome finishPostRecoveryFailure(
      SqlitePairPublicationMemberAttempt bookAttempt,
      SqlitePairPublicationMemberAttempt secretAttempt,
      boolean deterministicFailure) {
    if (SqlitePairPublicationMemberAttempt.eitherAttempted(bookAttempt, secretAttempt)) {
      return finishCompletionUncertain(bookAttempt.state(), secretAttempt.state());
    }
    return deterministicFailure ? finishDurablyRetainedPrepublication() : finishEvidenceBlocked();
  }

  StagedPairPublicationCommitOutcome finishDurablyRetainedPrepublication() {
    try {
      SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
          Objects.requireNonNull(recoveryRecord, "recoveryRecord"), directoryForcer);
    } catch (IOException | RuntimeException retentionFailure) {
      return finishEvidenceBlocked();
    }
    return finishPrepublicationRecoveryRequired(
        ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED);
  }

  StagedPairPublicationCommitOutcome finishEvidenceBlocked() {
    closeReservationsBestEffort("evidence-blocked");
    commitOutcome =
        new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
            bookTargetPath,
            ProtectedBookPairPublicationMemberState.UNESTABLISHED,
            secretTargetPath,
            ProtectedBookPairPublicationMemberState.UNESTABLISHED,
            null);
    return commitOutcome;
  }

  StagedPairPublicationCommitOutcome finishPrepublicationRecoveryRequired() {
    return finishPrepublicationRecoveryRequired(
        ProtectedBookPairPublicationRecoveryRecordState.DURABILITY_UNCONFIRMED);
  }

  StagedPairPublicationCommitOutcome finishPrepublicationRecoveryRequired(
      ProtectedBookPairPublicationRecoveryRecordState recoveryRecordState) {
    closeReservationsBestEffort("recovery-record durability uncertainty");
    commitOutcome =
        new ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired(
            bookTargetPath,
            secretTargetPath,
            Objects.requireNonNull(recoveryRecordState, "recoveryRecordState"),
            retainedPublicationStages());
    return commitOutcome;
  }

  StagedPairPublicationCommitOutcome finishAfterSuccessfulPublication() {
    try {
      SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
          Objects.requireNonNull(recoveryRecord, "recoveryRecord"), directoryForcer);
    } catch (IOException | RuntimeException completionEvidenceFailure) {
      return finishCompletionUncertain(
          ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE,
          ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE);
    }
    try {
      releaseRetainedPublicationAuthority();
    } catch (RuntimeException reservationReleaseFailure) {
      SqliteBestEffort.reportRetainedEvidenceReleaseFailure(
          "releasing retained " + publicationDescription + " reservations",
          reservationReleaseFailure);
    } finally {
      finishPassphrase();
    }
    commitOutcome = new StagedPairPublicationCommitOutcome.Published(retainedPublicationStages());
    return commitOutcome;
  }

  void finishAfterPreBoundaryFailure() {
    try {
      if (recoveryBoundaryMayExist) {
        closeReservations.run();
      } else {
        try {
          stagedBookFile.releaseRetained();
        } finally {
          try {
            stagedSecretFile.releaseRetained();
          } finally {
            closeReservations.run();
          }
        }
      }
    } finally {
      finishPassphrase();
    }
  }

  private void closeReservationsBestEffort(String phase) {
    try {
      closeReservations.run();
    } catch (RuntimeException reservationReleaseFailure) {
      SqliteBestEffort.reportRetainedEvidenceReleaseFailure(
          "releasing reservations after " + phase + " " + publicationDescription,
          reservationReleaseFailure);
    } finally {
      finishPassphrase();
    }
  }

  private void finishPassphrase() {
    closeUnusedPassphrase.run();
    finished = true;
  }

  private void releaseRetainedPublicationAuthority() {
    try {
      stagedBookFile.releaseRetained();
    } finally {
      try {
        stagedSecretFile.releaseRetained();
      } finally {
        closeReservations.run();
      }
    }
  }

  private ProtectedBookPairPublicationRetention retainedPublicationStages() {
    return new ProtectedBookPairPublicationRetention(
        new ArtifactPublicationResult(
            bookTargetPath, new ArtifactPublicationRetention(stagedBookFile.stagedPath())),
        new ArtifactPublicationResult(
            secretTargetPath, new ArtifactPublicationRetention(stagedSecretFile.stagedPath())));
  }
}
