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
        Files::createLink);
  }

  static SqliteStagedBackupPair create(
      SqliteOwnedStagedArtifact stagedBackupFile,
      Path finalBackupFilePath,
      SqliteOwnedStagedArtifact stagedBackupBookKeyFile,
      Path finalBackupBookKeyFilePath,
      SqliteBookPassphrase backupPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupKeyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupFileLinkCreator) {
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
        null);
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
        null);
  }

  static SqliteStagedBackupPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      byte[] backupPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupKeyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupFileLinkCreator,
      @Nullable SqliteOwnedDestinationReservation backupFileReservation,
      @Nullable SqliteOwnedDestinationReservation backupKeyReservation) {
    return new SqliteStagedBackupPair(
        artifacts,
        backupPassphraseBytes,
        verificationSupport,
        backupKeyLinkCreator,
        backupFileLinkCreator,
        backupFileReservation,
        backupKeyReservation);
  }

  private static byte[] ownedPassphraseBytes(SqliteBookPassphrase passphrase) {
    try (SqliteBookPassphrase ownedPassphrase =
        Objects.requireNonNull(passphrase, "backupPassphrase")) {
      return ownedPassphrase.utf8BytesCopy();
    }
  }
}
