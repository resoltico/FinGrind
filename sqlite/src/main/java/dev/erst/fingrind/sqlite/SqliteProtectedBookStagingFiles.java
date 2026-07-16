package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Owns filesystem and native-SQLite mutations used while assembling protected-book stages. */
final class SqliteProtectedBookStagingFiles {
  private SqliteProtectedBookStagingFiles() {}

  static void requireRegularNonSymlinkFile(Path normalizedPath) {
    if (!Files.isRegularFile(normalizedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteCallerPathContractException(
          normalizedPath,
          SqliteCallerPathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
          "The FinGrind protected book path must resolve to a regular non-symlink file: "
              + SqliteMachinePaths.absoluteValue(normalizedPath));
    }
  }

  static void exportBackupUsingSqlite(
      Path normalizedBookPath, Path stagedBackupFilePath, SqliteBookPassphrase sourcePassphrase) {
    try (SqliteBookPassphrase ignored = sourcePassphrase;
        SqliteNativeDatabase sourceDatabase =
            SqliteNativeConnections.openWithoutRollbackArtifactWarning(
                normalizedBookPath, sourcePassphrase, SqliteNativeOpenMode.READ_WRITE_EXISTING)) {
      sourceDatabase.executeStatement(
          "vacuum into '" + escapeSqlLiteral(stagedBackupFilePath.toString()) + "'");
      hardenBookArtifacts(stagedBackupFilePath);
    }
  }

  static void rekeyBookCopy(
      Path normalizedBookPath,
      SqliteBookPassphrase sourcePassphrase,
      SqliteBookPassphrase replacementPassphrase) {
    try (SqliteBookPassphrase ignoredSource = sourcePassphrase;
        SqliteBookPassphrase ignoredReplacement = replacementPassphrase;
        SqliteBookPassphrase resolvedSourcePassphrase = sourcePassphrase.copy();
        SqliteBookPassphrase resolvedReplacementPassphrase = replacementPassphrase.copy();
        SqliteNativeDatabase sourceDatabase =
            SqliteNativeConnections.openWithoutRollbackArtifactWarning(
                normalizedBookPath,
                resolvedSourcePassphrase,
                SqliteNativeOpenMode.READ_WRITE_EXISTING)) {
      SqliteNativeKeyConfiguration.rekey(sourceDatabase, resolvedReplacementPassphrase);
      hardenBookArtifacts(normalizedBookPath);
    }
  }

  static void ensureSecureBackupFileParentDirectory(Path artifactPath) {
    try {
      SqliteBookFileSecurity.ensureSecureParentDirectory(artifactPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to secure the parent directory for "
              + SqliteMachinePaths.absoluteValue(artifactPath)
              + ".",
          exception);
    }
  }

  static void ensureSecureBackupKeyFileParentDirectory(Path artifactPath) {
    try {
      SqliteBookKeyFileSecurity.ensureSecureParentDirectory(artifactPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to secure the parent directory for "
              + SqliteMachinePaths.absoluteValue(artifactPath)
              + ".",
          exception);
    }
  }

  static void hardenBookArtifacts(Path artifactPath) {
    try {
      SqliteBookFileSecurity.hardenBookArtifacts(artifactPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to harden the FinGrind protected-book artifacts for "
              + SqliteMachinePaths.absoluteValue(artifactPath)
              + ".",
          exception);
    }
  }

  static void resetStagedSecretFile(Path stagedSecretFilePath) {
    resetStagedSecretFile(stagedSecretFilePath, Files::deleteIfExists);
  }

  static void resetStagedSecretFile(
      Path stagedSecretFilePath, SqliteProtectedBookPublicationSupport.PathDeleter deleter) {
    try {
      deleter.delete(stagedSecretFilePath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to stage the generated FinGrind backup key artifact.", exception);
    }
  }

  static String escapeSqlLiteral(String value) {
    return value.replace("'", "''");
  }

  static void deleteQuietlyIfPresent(@Nullable Path path) {
    if (path != null) {
      SqliteFileCleanup.deleteQuietly(path);
    }
  }
}
