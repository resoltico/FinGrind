package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Staged restored live-book pair that publishes one re-encrypted book and key file together. */
final class SqliteStagedRestoredBookPair implements StagedRestoredBookPair {
  enum CommitFailureDisposition {
    PREPUBLICATION_RECOVERY_REQUIRED,
    COMPLETION_UNCERTAIN,
    POSTPUBLICATION_RECOVERY_REQUIRED,
    PREBOUNDARY_UNEXPECTED,
    POSTBOUNDARY_UNEXPECTED
  }

  private final SqliteOwnedStagedArtifact stagedBookFile;
  private final SqliteOwnedStagedArtifact stagedBookKeyFile;
  private final SqliteProtectedBookVerificationSupport verificationSupport;
  private final SqliteRestoredBookPairPublication publication;
  private final SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer;
  private final SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer
      recoveryRecordFileForcer;
  private final SqliteStagedPassphrase restoredPassphrase;
  private final SqliteStagedPairPublicationFinalizer finalizer;

  SqliteStagedRestoredBookPair(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      byte[] restoredPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteRestoredBookPairPublication publication,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer) {
    SqliteStagedProtectedBookPairArtifacts checkedArtifacts =
        Objects.requireNonNull(artifacts, "artifacts");
    this.stagedBookFile = checkedArtifacts.stagedBookFile();
    this.stagedBookKeyFile = checkedArtifacts.stagedSecretFile();
    this.restoredPassphrase =
        new SqliteStagedPassphrase("staged restored-book passphrase", restoredPassphraseBytes);
    this.verificationSupport = Objects.requireNonNull(verificationSupport, "verificationSupport");
    this.publication = Objects.requireNonNull(publication, "publication");
    this.directoryForcer = Objects.requireNonNull(directoryForcer, "directoryForcer");
    this.recoveryRecordFileForcer =
        Objects.requireNonNull(recoveryRecordFileForcer, "recoveryRecordFileForcer");
    this.finalizer =
        new SqliteStagedPairPublicationFinalizer(
            publication.bookTargetPath(),
            publication.secretTargetPath(),
            stagedBookFile,
            stagedBookKeyFile,
            restoredPassphrase::closeUnused,
            publication::closeReservations,
            directoryForcer,
            "protected-book pair publication");
  }

  @Override
  public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
      verifyInitializedRestoredBook() {
    return MaintenanceDecision.accepted(
        verificationSupport.verifyResolvedBook(
            stagedBookFile.stagedPath(), restoredPassphrase.copy()));
  }

  @Override
  public StagedPairPublicationCommitOutcome commit(ProtectedBookPairPublicationBinding binding) {
    Objects.requireNonNull(binding, "binding");
    StagedPairPublicationCommitOutcome cachedOutcome = finalizer.cachedOutcome();
    if (cachedOutcome != null) {
      return cachedOutcome;
    }
    if (finalizer.isFinished()) {
      throw new IllegalStateException(
          "The staged restored protected-book pair is already finished.");
    }
    SqlitePairPublicationMemberAttempt secretAttempt = new SqlitePairPublicationMemberAttempt();
    SqlitePairPublicationMemberAttempt bookAttempt = new SqlitePairPublicationMemberAttempt();
    boolean durableRecoveryBoundaryReached = false;
    try {
      stagedBookFile.requireIntactFor(publication.bookTargetPath());
      stagedBookKeyFile.requireIntactFor(publication.secretTargetPath());
      SqlitePairPublicationDurability.forceStagedRecoveryMembers(
          stagedBookFile,
          publication.bookTargetPath(),
          stagedBookKeyFile,
          publication.secretTargetPath(),
          directoryForcer);
      // Record the durable boundary before either final member can run. If a later fatal Error
      // escapes rather than producing a recoverable result, close() still retains this evidence
      // and never releases the staged members as though the failure preceded publication.
      SqliteProtectedBookPairPublicationRecord recoveryRecord =
          SqliteProtectedBookPairPublicationRecord.create(
              publication.bookTargetPath(),
              publication.secretTargetPath(),
              stagedBookFile.stagedPath(),
              stagedBookKeyFile.stagedPath(),
              publication.targetPolicy(),
              binding,
              directoryForcer);
      finalizer.recordRecoveryBoundary(recoveryRecord);
      durableRecoveryBoundaryReached = true;

      // Preserve NOT_ATTEMPTED when the deterministic boundary check refuses before any secret
      // publication can be attempted. publishSecret repeats it inside the primitive beside link.
      if (!selectedBookTargetIsCurrent(recoveryRecord)) {
        return finalizer.finishPrepublicationRecoveryRequired();
      }
      publication.publishSecret(
          stagedBookKeyFile,
          () -> {
            forceAndRequireRestoredRecoveryBoundary(
                stagedBookKeyFile,
                publication.secretTargetPath(),
                Objects.requireNonNull(recoveryRecord, "recoveryRecord"),
                false);
            requireSelectedBookTargetForPublication(recoveryRecord);
            publication.requireCapabilityCurrent(
                publication.secretTargetPath(),
                SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
          },
          secretAttempt::markAttempted);
      secretAttempt.markPublishedDurabilityUnconfirmed();
      SqlitePairPublicationDurability.forcePublishedDirectory(
          directoryForcer,
          SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
              .GENERATED_SECRET_PUBLICATION,
          publication.secretTargetPath());
      secretAttempt.markPublishedDurable();

      if (!selectedBookTargetIsCurrent(recoveryRecord)) {
        return finalizer.finishCompletionUncertain(bookAttempt.state(), secretAttempt.state());
      }
      publication.publishBook(
          stagedBookFile,
          () -> {
            forceAndRequireRestoredRecoveryBoundary(
                stagedBookFile,
                publication.bookTargetPath(),
                Objects.requireNonNull(recoveryRecord, "recoveryRecord"),
                true);
            requireSelectedBookTargetForPublication(recoveryRecord);
            publication.requireCapabilityCurrent(
                publication.bookTargetPath(),
                publication.targetPolicy()
                        == ProtectedBookMaintenanceStore.RestoredBookTargetPolicy.REPLACE_SELECTED
                    ? SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE
                    : SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
          },
          bookAttempt::markAttempted);
      bookAttempt.markPublishedDurabilityUnconfirmed();
      SqlitePairPublicationDurability.forcePublishedDirectory(
          directoryForcer,
          SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.BOOK_PUBLICATION,
          publication.bookTargetPath());
      bookAttempt.markPublishedDurable();
    } catch (Exception failure) {
      return handleCommitFailure(
          failure, durableRecoveryBoundaryReached, bookAttempt, secretAttempt);
    }
    return finalizer.finishAfterSuccessfulPublication();
  }

  private StagedPairPublicationCommitOutcome handleCommitFailure(
      Exception failure,
      boolean durableRecoveryBoundaryReached,
      SqlitePairPublicationMemberAttempt bookAttempt,
      SqlitePairPublicationMemberAttempt secretAttempt) {
    return switch (
        failureDisposition(
            failure,
            durableRecoveryBoundaryReached,
            SqlitePairPublicationMemberAttempt.eitherAttempted(bookAttempt, secretAttempt))) {
      case PREPUBLICATION_RECOVERY_REQUIRED -> finalizer.finishPrepublicationRecoveryRequired();
      case COMPLETION_UNCERTAIN ->
          finalizer.finishCompletionUncertain(bookAttempt.state(), secretAttempt.state());
      case POSTPUBLICATION_RECOVERY_REQUIRED ->
          finalizer.finishPostRecoveryFailure(bookAttempt, secretAttempt, true);
      case POSTBOUNDARY_UNEXPECTED ->
          finalizer.finishPostRecoveryFailure(bookAttempt, secretAttempt, false);
      case PREBOUNDARY_UNEXPECTED -> {
        finalizer.finishAfterPreBoundaryFailure();
        throw unexpectedPreboundaryFailure(failure);
      }
    };
  }

  static CommitFailureDisposition failureDisposition(
      Exception failure,
      boolean durableRecoveryBoundaryReached,
      boolean finalMemberAttempted) {
    Exception checkedFailure = Objects.requireNonNull(failure, "failure");
    if (checkedFailure
        instanceof
        SqliteProtectedBookPairPublicationRecord.RecoveryRecordDurabilityUnconfirmedException) {
      return CommitFailureDisposition.PREPUBLICATION_RECOVERY_REQUIRED;
    }
    if (checkedFailure
        instanceof
        SqliteProtectedBookPublicationSupport.FinalMemberPublicationGuardRejectedException
            guardFailure) {
      return guardFailure.member() == SqliteProtectedBookPublicationSupport.FinalMember.SECRET
          ? CommitFailureDisposition.PREPUBLICATION_RECOVERY_REQUIRED
          : CommitFailureDisposition.COMPLETION_UNCERTAIN;
    }
    if (!durableRecoveryBoundaryReached) {
      return CommitFailureDisposition.PREBOUNDARY_UNEXPECTED;
    }
    if (checkedFailure instanceof SqliteGeneratedSecretTargetOccupiedException
        || failure instanceof SqliteCallerPathContractException
        || failure instanceof java.nio.file.FileAlreadyExistsException) {
      return CommitFailureDisposition.POSTPUBLICATION_RECOVERY_REQUIRED;
    }
    if (checkedFailure instanceof IOException) {
      return finalMemberAttempted
          ? CommitFailureDisposition.COMPLETION_UNCERTAIN
          : CommitFailureDisposition.PREPUBLICATION_RECOVERY_REQUIRED;
    }
    return CommitFailureDisposition.POSTBOUNDARY_UNEXPECTED;
  }

  private RuntimeException unexpectedPreboundaryFailure(Exception failure) {
    if (failure instanceof RuntimeException runtimeFailure) {
      return runtimeFailure;
    }
    return new IllegalStateException(
        "Failed to publish the restored FinGrind live-book pair at "
            + SqliteMachinePaths.absoluteValue(publication.bookTargetPath())
            + ".",
        failure);
  }

  @Override
  public void retainUnpublishedArtifacts() {
    if (!finalizer.isFinished()) {
      finalizer.finishAfterPreBoundaryFailure();
    }
  }

  @Override
  public void close() {
    if (!finalizer.isFinished()) {
      retainUnpublishedArtifacts();
    }
  }

  /** Revalidates the common immutable-evidence boundary before one restored member primitive. */
  private void forceAndRequireRestoredRecoveryBoundary(
      SqliteOwnedStagedArtifact stagedArtifact,
      Path finalPath,
      SqliteProtectedBookPairPublicationRecord record,
      boolean bookMember)
      throws IOException {
    SqlitePairPublicationDurability.forceAndRequireRecoveryBoundary(
        record, stagedArtifact, finalPath, bookMember, directoryForcer, recoveryRecordFileForcer);
  }

  /** Revalidates the selected rekey target at the final-member publication boundary. */
  private void requireSelectedBookTargetForPublication(
      @org.jspecify.annotations.Nullable SqliteProtectedBookPairPublicationRecord record)
      throws IOException {
    if (!selectedBookTargetIsCurrent(record)) {
      throw new IOException(
          "The selected rekey book target changed after durable recovery evidence was recorded.");
    }
  }

  private boolean selectedBookTargetIsCurrent(
      @org.jspecify.annotations.Nullable SqliteProtectedBookPairPublicationRecord record) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "recoveryRecord");
    return publication.targetPolicy()
            != ProtectedBookMaintenanceStore.RestoredBookTargetPolicy.REPLACE_SELECTED
        || checkedRecord.finalBookMatches()
        || checkedRecord.replaceTargetMatches();
  }
}
