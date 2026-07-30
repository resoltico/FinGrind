package dev.erst.fingrind.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Builds staged backup-pair lifecycle owners with explicit publication dependencies. */
final class SqliteStagedBackupPairFactory {
  private SqliteStagedBackupPairFactory() {}

  static SqliteStagedBackupPair create(
      SqliteOwnedStagedArtifact stagedBackupFile,
      Path finalBackupFilePath,
      SqliteOwnedStagedArtifact stagedBackupBookKeyFile,
      Path finalBackupBookKeyFilePath,
      SqliteBookPassphrase backupPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    return create(
        stagedBackupFile,
        finalBackupFilePath,
        stagedBackupBookKeyFile,
        finalBackupBookKeyFilePath,
        backupPassphrase,
        verificationSupport,
        Files::createLink,
        Files::createLink,
        SqliteProtectedBookPublicationSupport.productionPairDirectoryForcer());
  }

  static SqliteStagedBackupPair create(
      SqliteOwnedStagedArtifact stagedBackupFile,
      Path finalBackupFilePath,
      SqliteOwnedStagedArtifact stagedBackupBookKeyFile,
      Path finalBackupBookKeyFilePath,
      SqliteBookPassphrase backupPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupKeyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupFileLinkCreator,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer) {
    return create(
        new SqliteStagedProtectedBookPairArtifacts(
            stagedBackupFile,
            finalBackupFilePath,
            stagedBackupBookKeyFile,
            finalBackupBookKeyFilePath),
        ownedPassphraseBytes(backupPassphrase),
        verificationSupport,
        backupKeyLinkCreator,
        backupFileLinkCreator,
        null,
        null,
        directoryForcer);
  }

  static SqliteStagedBackupPair create(
      SqliteOwnedStagedArtifact stagedBackupFile,
      Path finalBackupFilePath,
      SqliteOwnedStagedArtifact stagedBackupBookKeyFile,
      Path finalBackupBookKeyFilePath,
      byte[] backupPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupKeyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupFileLinkCreator) {
    return create(
        new SqliteStagedProtectedBookPairArtifacts(
            stagedBackupFile,
            finalBackupFilePath,
            stagedBackupBookKeyFile,
            finalBackupBookKeyFilePath),
        backupPassphraseBytes,
        verificationSupport,
        backupKeyLinkCreator,
        backupFileLinkCreator,
        null,
        null,
        SqliteProtectedBookPublicationSupport.productionPairDirectoryForcer());
  }

  static SqliteStagedBackupPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      byte[] backupPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupKeyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupFileLinkCreator,
      @Nullable SqliteOwnedDestinationReservation backupFileReservation,
      @Nullable SqliteOwnedDestinationReservation backupKeyReservation,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer) {
    return create(
        artifacts,
        backupPassphraseBytes,
        verificationSupport,
        backupKeyLinkCreator,
        backupFileLinkCreator,
        backupFileReservation,
        backupKeyReservation,
        directoryForcer,
        SqliteOwnedRegularFileAccess::forceFile);
  }

  static SqliteStagedBackupPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      byte[] backupPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupKeyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupFileLinkCreator,
      @Nullable SqliteOwnedDestinationReservation backupFileReservation,
      @Nullable SqliteOwnedDestinationReservation backupKeyReservation,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer) {
    return create(
        artifacts,
        backupPassphraseBytes,
        verificationSupport,
        new SqliteStagedBackupPair.PublicationDependencies(
            backupKeyLinkCreator,
            backupFileLinkCreator,
            backupFileReservation,
            backupKeyReservation,
            directoryForcer,
            recoveryRecordFileForcer,
            acquireCapabilityWitnesses(artifacts)));
  }

  static SqliteStagedBackupPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      byte[] backupPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteStagedBackupPair.PublicationDependencies dependencies) {
    return new SqliteStagedBackupPair(
        artifacts, backupPassphraseBytes, verificationSupport, dependencies);
  }

  private static SqlitePublicationCapabilityWitness.Set acquireCapabilityWitnesses(
      SqliteStagedProtectedBookPairArtifacts artifacts) {
    SqliteStagedProtectedBookPairArtifacts checkedArtifacts =
        Objects.requireNonNull(artifacts, "artifacts");
    try {
      return SqlitePublicationCapabilityWitness.acquirePair(
          checkedArtifacts.bookTargetPath(),
          SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK,
          checkedArtifacts.secretTargetPath(),
          Files::createLink,
          SqliteProtectedBookPublicationSupport::moveReplacing);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(
          "Failed to acquire retained FinGrind backup publication capability witnesses.",
          exception);
    }
  }

  private static byte[] ownedPassphraseBytes(SqliteBookPassphrase passphrase) {
    try (SqliteBookPassphrase ownedPassphrase =
        Objects.requireNonNull(passphrase, "backupPassphrase")) {
      return ownedPassphrase.utf8BytesCopy();
    }
  }
}
