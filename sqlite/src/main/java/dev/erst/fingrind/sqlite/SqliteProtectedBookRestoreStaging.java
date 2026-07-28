package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.ArtifactPublicationStages;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Creates a rekeyed staged restored-book pair before publication. */
final class SqliteProtectedBookRestoreStaging {
  private SqliteProtectedBookRestoreStaging() {}

  static MaintenanceDecision<StagedRestoredBookPair> stageResolvedPair(
      Path normalizedSourceBookPath,
      SqlitePreparedPairPublication preparedPublication,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    Objects.requireNonNull(preparedPublication, "preparedPublication");
    return stageResolvedPair(
        normalizedSourceBookPath,
        preparedPublication.bookTargetPath(),
        preparedPublication.secretTargetPath(),
        preparedPublication.bookTargetPolicy(),
        sourcePassphrase,
        verificationSupport,
        SqliteProtectedBookStagingCheckpointListener.none(),
        SqliteBookKeyFileGenerator::generateIntoExistingOwnedStage,
        preparedPublication);
  }

  static MaintenanceDecision<StagedRestoredBookPair> stageResolvedPair(
      Path normalizedSourceBookPath,
      Path normalizedBookFilePath,
      Path normalizedBookKeyFilePath,
      RestoredBookTargetPolicy targetPolicy,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookStagingCheckpointListener checkpointListener,
      SqliteDistinctStagedSecret.Generator stagedSecretGenerator) {
    SqliteProtectedBookStagingTargetPreparation.prepareUnreservedRestoreTargets(
        normalizedBookFilePath, normalizedBookKeyFilePath);
    return stageResolvedPair(
        normalizedSourceBookPath,
        normalizedBookFilePath,
        normalizedBookKeyFilePath,
        targetPolicy,
        sourcePassphrase,
        verificationSupport,
        checkpointListener,
        stagedSecretGenerator,
        null);
  }

  static MaintenanceDecision<StagedRestoredBookPair> stageResolvedPair(
      Path normalizedSourceBookPath,
      Path normalizedBookFilePath,
      Path normalizedBookKeyFilePath,
      RestoredBookTargetPolicy targetPolicy,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookStagingCheckpointListener checkpointListener,
      SqliteDistinctStagedSecret.Generator stagedSecretGenerator,
      @Nullable SqlitePreparedPairPublication preparedPublication) {
    Objects.requireNonNull(checkpointListener, "checkpointListener");
    Objects.requireNonNull(stagedSecretGenerator, "stagedSecretGenerator");
    SqliteStagingSourcePassphraseLease sourceLease =
        SqliteStagingSourcePassphraseLease.take(sourcePassphrase);
    try {
      SqliteBookFileSecurity.requireSupportedSecureFilesystem(normalizedBookFilePath);
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(normalizedBookKeyFilePath);
      SqliteProtectedBookStagingTargetPreparation.ensureArtifactParents(
          normalizedBookFilePath, normalizedBookKeyFilePath);
      return stageCreatedArtifacts(
          normalizedSourceBookPath,
          normalizedBookFilePath,
          normalizedBookKeyFilePath,
          targetPolicy,
          sourceLease,
          verificationSupport,
          checkpointListener,
          stagedSecretGenerator,
          preparedPublication);
    } finally {
      sourceLease.wipe();
    }
  }

  private static MaintenanceDecision<StagedRestoredBookPair> stageCreatedArtifacts(
      Path normalizedSourceBookPath,
      Path normalizedBookFilePath,
      Path normalizedBookKeyFilePath,
      RestoredBookTargetPolicy targetPolicy,
      SqliteStagingSourcePassphraseLease sourceLease,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookStagingCheckpointListener checkpointListener,
      SqliteDistinctStagedSecret.Generator stagedSecretGenerator,
      @Nullable SqlitePreparedPairPublication preparedPublication) {
    @Nullable SqliteOwnedStagedArtifact stagedBookFile = null;
    @Nullable SqliteOwnedStagedArtifact stagedBookKeyFile = null;
    SqliteProtectedBookStagingCheckpoint activeCheckpoint =
        SqliteProtectedBookStagingCheckpoint.RESTORE_COPY;
    try {
      checkpointListener.reached(activeCheckpoint);
      SqliteOwnedStagedArtifact copiedBookStage =
          copySourceIntoExactRetainedStage(
              normalizedSourceBookPath, normalizedBookFilePath, preparedPublication);
      stagedBookFile = copiedBookStage;
      activeCheckpoint = SqliteProtectedBookStagingCheckpoint.RESTORE_SECRET_GENERATION;
      SqliteDistinctStagedSecret.GeneratedSecret generatedSecret =
          SqliteDistinctStagedSecret.generate(
              () ->
                  preparedPublication == null
                      ? SqliteOwnedStagedArtifact.create(
                          normalizedBookKeyFilePath, ".restore-key-", ".tmp")
                      : preparedPublication.createSecretStage(".restore-key-", ".tmp"),
              sourceLease.passphrase(),
              activeCheckpoint,
              checkpointListener,
              stagedSecretGenerator);
      stagedBookKeyFile = generatedSecret.stagedSecretFile();
      try (SqliteBookPassphrase restoredPassphrase = generatedSecret.passphrase()) {
        activeCheckpoint = SqliteProtectedBookStagingCheckpoint.RESTORE_REKEY;
        checkpointListener.reached(activeCheckpoint);
        SqliteProtectedBookStagingFiles.rekeyStagedBookCopy(
            copiedBookStage.stagedPath(),
            sourceLease.passphrase().copy(),
            restoredPassphrase.copy());
        SqlitePreparedPairPublication.@Nullable PublicationReservations reservations =
            preparedPublication == null ? null : preparedPublication.transferReservations();
        SqliteStagedRestoredBookPair stagedRestoredBookPair =
            createStagedRestoredBookPair(
                new SqliteStagedProtectedBookPairArtifacts(
                    copiedBookStage,
                    normalizedBookFilePath,
                    Objects.requireNonNull(stagedBookKeyFile, "stagedBookKeyFile"),
                    normalizedBookKeyFilePath),
                targetPolicy,
                restoredPassphrase.utf8BytesCopy(),
                verificationSupport,
                reservations);
        return MaintenanceDecision.accepted(stagedRestoredBookPair);
      }
    } catch (ContractFailureException exception) {
      SqliteProtectedBookStagingFailure.releaseAllRetained(stagedBookFile, stagedBookKeyFile);
      throw exception;
    } catch (IOException | RuntimeException exception) {
      SqliteOwnedStagedArtifact.releaseAllRetained(stagedBookFile, stagedBookKeyFile);
      return SqliteProtectedBookStagingFailure.at(
          normalizedBookFilePath, "bookFilePath", activeCheckpoint);
    }
  }

  /** Copies the source through one fresh owner-private stage, never reopening an existing path. */
  private static SqliteOwnedStagedArtifact copySourceIntoExactRetainedStage(
      Path normalizedSourceBookPath,
      Path normalizedBookFilePath,
      @Nullable SqlitePreparedPairPublication preparedPublication)
      throws IOException {
    if (preparedPublication != null) {
      SqliteOwnedStagedArtifact stagedBook =
          preparedPublication.createBookStage(".restore-", ".sqlite");
      try {
        copySourceIntoExistingOwnedStage(normalizedSourceBookPath, stagedBook.stagedPath());
        return stagedBook;
      } catch (IOException | RuntimeException failure) {
        stagedBook.releaseRetained();
        throw failure;
      }
    }
    Path normalizedTarget =
        Objects.requireNonNull(normalizedBookFilePath, "normalizedBookFilePath")
            .toAbsolutePath()
            .normalize();
    Path parent = Objects.requireNonNull(normalizedTarget.getParent(), "normalized book parent");
    Path stagedPath =
        ArtifactPublicationStages.createAndCopy(
            parent, ".fingrind-stage.restore-", ".tmp", normalizedSourceBookPath);
    return SqliteOwnedStagedArtifact.recordExisting(normalizedTarget, stagedPath);
  }

  /** Copies one nofollow source into an exact already-owned stage and force-confirms its bytes. */
  static void copySourceIntoExistingOwnedStage(Path sourcePath, Path stagedPath)
      throws IOException {
    try (InputStream source = SqliteSecureRegularFileAccess.openRead(sourcePath);
        FileChannel destination = SqliteSecureRegularFileAccess.openTruncatingWrite(stagedPath)) {
      byte[] buffer = new byte[16 * 1024];
      int read = source.read(buffer);
      while (read >= 0) {
        if (read == 0) {
          throw new IOException("Failed to read the complete protected-book restore source.");
        }
        ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, read);
        while (bytes.hasRemaining()) {
          if (destination.write(bytes) <= 0) {
            throw new IOException("Failed to write the complete protected-book restore stage.");
          }
        }
        read = source.read(buffer);
      }
      destination.force(true);
    }
  }

  private static SqliteStagedRestoredBookPair createStagedRestoredBookPair(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      RestoredBookTargetPolicy targetPolicy,
      byte[] restoredPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqlitePreparedPairPublication.@Nullable PublicationReservations reservations) {
    SqliteRestoredBookPairPublication.Operators operators =
        new SqliteRestoredBookPairPublication.Operators(
            Files::createLink,
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing);
    if (reservations == null) {
      return SqliteStagedRestoredBookPairFactory.create(
          artifacts,
          targetPolicy,
          restoredPassphraseBytes,
          verificationSupport,
          operators,
          null,
          null,
          SqliteProtectedBookPublicationSupport.productionPairDirectoryForcer());
    }
    return SqliteStagedRestoredBookPairFactory.create(
        artifacts,
        targetPolicy,
        restoredPassphraseBytes,
        verificationSupport,
        new SqliteStagedRestoredBookPairFactory.PublicationDependencies(
            operators,
            reservations.bookReservation(),
            reservations.secretReservation(),
            SqliteProtectedBookPublicationSupport.productionPairDirectoryForcer(),
            SqliteSecureRegularFileAccess::forceFile,
            reservations.capabilityWitnesses()));
  }
}
