package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Staged encrypted backup pair implementation. */
final class SqliteStagedBackupPair implements StagedBackupPair {
  /** Closed classification of a failed staged-backup pair publication. */
  enum CommitFailureDisposition {
    PREPUBLICATION_RECOVERY_REQUIRED,
    DURABLY_RETAINED_PREPUBLICATION,
    COMPLETION_UNCERTAIN,
    POSTPUBLICATION_RECOVERY_REQUIRED,
    PREBOUNDARY_UNEXPECTED,
    POSTBOUNDARY_UNEXPECTED
  }

  private final SqliteOwnedStagedArtifact stagedBackupFile;
  private final Path finalBackupFilePath;
  private final SqliteOwnedStagedArtifact stagedBackupBookKeyFile;
  private final Path finalBackupBookKeyFilePath;
  private final SqliteProtectedBookVerificationSupport verificationSupport;
  private final SqliteBackupPairPublication publication;
  private final SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer;
  private final SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer
      recoveryRecordFileForcer;
  private final SqliteStagedPassphrase backupPassphrase;
  private final SqliteStagedBackupArtifact artifact;
  private final SqliteStagedPairPublicationFinalizer finalizer;

  SqliteStagedBackupPair(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      byte[] backupPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      PublicationDependencies dependencies) {
    SqliteStagedProtectedBookPairArtifacts checkedArtifacts =
        Objects.requireNonNull(artifacts, "artifacts");
    this.stagedBackupFile = checkedArtifacts.stagedBookFile();
    this.finalBackupFilePath = checkedArtifacts.bookTargetPath();
    this.stagedBackupBookKeyFile = checkedArtifacts.stagedSecretFile();
    this.finalBackupBookKeyFilePath = checkedArtifacts.secretTargetPath();
    this.backupPassphrase =
        new SqliteStagedPassphrase(
            "staged protected-book backup passphrase", backupPassphraseBytes);
    this.verificationSupport = Objects.requireNonNull(verificationSupport, "verificationSupport");
    this.publication =
        new SqliteBackupPairPublication(
            dependencies.backupKeyLinkCreator(),
            dependencies.backupFileLinkCreator(),
            dependencies.backupFileReservation(),
            dependencies.backupKeyReservation(),
            dependencies.capabilityWitnesses());
    this.directoryForcer =
        Objects.requireNonNull(dependencies.directoryForcer(), "directoryForcer");
    this.recoveryRecordFileForcer =
        Objects.requireNonNull(dependencies.recoveryRecordFileForcer(), "recoveryRecordFileForcer");
    this.artifact = new SqliteStagedBackupArtifact(stagedBackupFile, finalBackupFilePath);
    this.finalizer =
        new SqliteStagedPairPublicationFinalizer(
            finalBackupFilePath,
            finalBackupBookKeyFilePath,
            stagedBackupFile,
            stagedBackupBookKeyFile,
            this::closeUnusedBackupPassphrase,
            publication::closeReservations,
            directoryForcer,
            "protected-book backup publication");
  }

  @Override
  public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
      verifyInitializedBackup() {
    artifact.requireUnsealed();
    return MaintenanceDecision.accepted(
        verificationSupport.verifyResolvedBook(
            stagedBackupFile.stagedPath(), backupPassphrase.copy()));
  }

  @Override
  public byte[] snapshot() {
    return artifact.snapshot();
  }

  @Override
  public void sealArtifact(byte[] artifact) {
    this.artifact.seal(artifact);
  }

  @Override
  public StagedPairPublicationCommitOutcome commit(ProtectedBookPairPublicationBinding binding) {
    Objects.requireNonNull(binding, "binding");
    StagedPairPublicationCommitOutcome cachedOutcome = finalizer.cachedOutcome();
    if (cachedOutcome != null) {
      return cachedOutcome;
    }
    if (finalizer.isFinished()) {
      throw new IllegalStateException("The staged protected-book backup pair is already finished.");
    }
    artifact.requireSealed();
    SqlitePairPublicationMemberAttempt secretAttempt = new SqlitePairPublicationMemberAttempt();
    SqlitePairPublicationMemberAttempt bookAttempt = new SqlitePairPublicationMemberAttempt();
    PublicationProgress progress = new PublicationProgress();
    try {
      publishBoundPair(binding, secretAttempt, bookAttempt, progress);
    } catch (Exception failure) {
      return handleCommitFailure(failure, progress, bookAttempt, secretAttempt);
    }
    return finalizer.finishAfterSuccessfulPublication();
  }

  private StagedPairPublicationCommitOutcome handleCommitFailure(
      Exception failure,
      PublicationProgress progress,
      SqlitePairPublicationMemberAttempt bookAttempt,
      SqlitePairPublicationMemberAttempt secretAttempt) {
    return switch (failureDisposition(failure, progress.recoveryBoundaryReached())) {
      case PREPUBLICATION_RECOVERY_REQUIRED -> finalizer.finishPrepublicationRecoveryRequired();
      case DURABLY_RETAINED_PREPUBLICATION -> finalizer.finishDurablyRetainedPrepublication();
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
      Exception failure, boolean recoveryBoundaryReached) {
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
          ? CommitFailureDisposition.DURABLY_RETAINED_PREPUBLICATION
          : CommitFailureDisposition.COMPLETION_UNCERTAIN;
    }
    if (!recoveryBoundaryReached) {
      return CommitFailureDisposition.PREBOUNDARY_UNEXPECTED;
    }
    if (checkedFailure instanceof SqliteGeneratedSecretTargetOccupiedException
        || checkedFailure instanceof SqliteCallerPathContractException
        || checkedFailure instanceof java.nio.file.FileAlreadyExistsException) {
      return CommitFailureDisposition.POSTPUBLICATION_RECOVERY_REQUIRED;
    }
    return CommitFailureDisposition.POSTBOUNDARY_UNEXPECTED;
  }

  private static RuntimeException unexpectedPreboundaryFailure(Exception failure) {
    if (failure instanceof RuntimeException runtimeFailure) {
      return runtimeFailure;
    }
    return new IllegalStateException("Failed to publish the staged FinGrind backup pair.", failure);
  }

  private void publishBoundPair(
      ProtectedBookPairPublicationBinding binding,
      SqlitePairPublicationMemberAttempt secretAttempt,
      SqlitePairPublicationMemberAttempt bookAttempt,
      PublicationProgress progress)
      throws IOException {
    try (SqliteBookPassphrase ignored = backupPassphrase.take()) {
      stagedBackupFile.requireIntactFor(finalBackupFilePath);
      stagedBackupBookKeyFile.requireIntactFor(finalBackupBookKeyFilePath);
      SqlitePairPublicationDurability.forceStagedRecoveryMembers(
          stagedBackupFile,
          finalBackupFilePath,
          stagedBackupBookKeyFile,
          finalBackupBookKeyFilePath,
          directoryForcer);
      SqliteProtectedBookPairPublicationRecord recoveryRecord =
          SqliteProtectedBookPairPublicationRecord.create(
              finalBackupFilePath,
              finalBackupBookKeyFilePath,
              stagedBackupFile.stagedPath(),
              stagedBackupBookKeyFile.stagedPath(),
              ProtectedBookMaintenanceStore.RestoredBookTargetPolicy.REQUIRE_ABSENT,
              binding,
              directoryForcer);
      finalizer.recordRecoveryBoundary(recoveryRecord);
      progress.markRecoveryBoundaryReached();
      publishSecretMember(secretAttempt, recoveryRecord);
      publishBookMember(bookAttempt, recoveryRecord);
    }
  }

  private void publishSecretMember(
      SqlitePairPublicationMemberAttempt secretAttempt,
      SqliteProtectedBookPairPublicationRecord recoveryRecord)
      throws IOException {
    publication.publishKey(
        stagedBackupBookKeyFile,
        finalBackupBookKeyFilePath,
        finalBackupBookKeyFilePath,
        () ->
            forceAndRequireBackupRecoveryBoundary(
                stagedBackupBookKeyFile, finalBackupBookKeyFilePath, recoveryRecord, false),
        secretAttempt::markAttempted);
    secretAttempt.markPublishedDurabilityUnconfirmed();
    SqlitePairPublicationDurability.forcePublishedDirectory(
        directoryForcer,
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
            .GENERATED_SECRET_PUBLICATION,
        finalBackupBookKeyFilePath);
    secretAttempt.markPublishedDurable();
  }

  private void publishBookMember(
      SqlitePairPublicationMemberAttempt bookAttempt,
      SqliteProtectedBookPairPublicationRecord recoveryRecord)
      throws IOException {
    publication.publishBook(
        stagedBackupFile,
        finalBackupFilePath,
        () ->
            forceAndRequireBackupRecoveryBoundary(
                stagedBackupFile, finalBackupFilePath, recoveryRecord, true),
        bookAttempt::markAttempted);
    bookAttempt.markPublishedDurabilityUnconfirmed();
    SqlitePairPublicationDurability.forcePublishedDirectory(
        directoryForcer,
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.BOOK_PUBLICATION,
        finalBackupFilePath);
    bookAttempt.markPublishedDurable();
  }

  @Override
  public void retainUnpublishedArtifacts() {
    if (finalizer.isFinished()) {
      return;
    }
    finalizer.finishAfterPreBoundaryFailure();
  }

  @Override
  public void close() {
    if (!finalizer.isFinished()) {
      retainUnpublishedArtifacts();
    }
  }

  /** Closes the staged backup passphrase without releasing retained stage authority. */
  private void closeUnusedBackupPassphrase() {
    backupPassphrase.closeUnused();
  }

  /** Revalidates backup capability immediately after the common immutable-evidence boundary. */
  private void forceAndRequireBackupRecoveryBoundary(
      SqliteOwnedStagedArtifact stagedArtifact,
      Path finalPath,
      SqliteProtectedBookPairPublicationRecord record,
      boolean bookMember)
      throws IOException {
    SqlitePairPublicationDurability.forceAndRequireRecoveryBoundary(
        record, stagedArtifact, finalPath, bookMember, directoryForcer, recoveryRecordFileForcer);
    publication.requireCapabilityCurrent(
        finalPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
  }

  /** Publication dependencies whose ownership is transferred into one staged backup pair. */
  record PublicationDependencies(
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupKeyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupFileLinkCreator,
      @Nullable SqliteOwnedDestinationReservation backupFileReservation,
      @Nullable SqliteOwnedDestinationReservation backupKeyReservation,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {}

  /** Records whether a durable recovery record exists while a member publication is in flight. */
  private static final class PublicationProgress {
    private boolean recoveryBoundaryReached;

    private boolean recoveryBoundaryReached() {
      return recoveryBoundaryReached;
    }

    private void markRecoveryBoundaryReached() {
      recoveryBoundaryReached = true;
    }
  }
}
