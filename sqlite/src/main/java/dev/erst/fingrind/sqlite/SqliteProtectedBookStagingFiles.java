package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Owns filesystem and native-SQLite mutations used while assembling protected-book stages. */
final class SqliteProtectedBookStagingFiles {
  private SqliteProtectedBookStagingFiles() {}

  static void requireRegularNonSymlinkFile(Path normalizedPath) {
    if (!Files.isRegularFile(normalizedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteCallerPathContractException(
          normalizedPath,
          SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
          "The FinGrind protected book path must resolve to a regular non-symlink file: "
              + SqliteMachinePaths.absoluteValue(normalizedPath));
    }
  }

  static void exportBackupUsingSqlite(
      Path normalizedBookPath, Path stagedBackupFilePath, SqliteBookPassphrase sourcePassphrase) {
    try (SqliteBookPassphrase ignored = sourcePassphrase;
        SqliteBookPassphrase stagedBackupPassphrase = sourcePassphrase.copy()) {
      try (SqliteNativeDatabase sourceDatabase =
              runBackupStep(
                  SqliteProtectedBookStagingCheckpoint.BACKUP_SOURCE_OPEN,
                  () ->
                      SqliteNativeConnections.open(
                          normalizedBookPath, sourcePassphrase, SqliteNativeOpenMode.READ_ONLY));
          SqliteNativeDatabase stagedBackupDatabase =
              runBackupStep(
                  SqliteProtectedBookStagingCheckpoint.BACKUP_STAGE_OPEN,
                  () ->
                      SqliteNativeConnections.open(
                          stagedBackupFilePath,
                          stagedBackupPassphrase,
                          SqliteNativeOpenMode.READ_WRITE_EXISTING_STAGE))) {
        runBackupStep(
            SqliteProtectedBookStagingCheckpoint.BACKUP_COPY,
            () -> {
              SqliteNativeBackups.copyMainDatabase(sourceDatabase, stagedBackupDatabase);
              return null;
            });
      }
    }
  }

  private static <T> T runBackupStep(
      SqliteProtectedBookStagingCheckpoint checkpoint, Supplier<T> operation) {
    try {
      return operation.get();
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new BackupExportFailure(checkpoint, exception);
    }
  }

  /** Carries the public-safe boundary that failed during native backup export. */
  static final class BackupExportFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final SqliteProtectedBookStagingCheckpoint checkpoint;

    BackupExportFailure(SqliteProtectedBookStagingCheckpoint checkpoint, RuntimeException cause) {
      super(cause);
      this.checkpoint = checkpoint;
    }

    SqliteProtectedBookStagingCheckpoint checkpoint() {
      return checkpoint;
    }

    String publicFailureMessage() {
      String prefix = checkpoint.failureMessage();
      Throwable cause = getCause();
      if (cause instanceof SqliteNativeException nativeFailure) {
        return prefix + " SQLite reported " + nativeFailure.resultName() + ".";
      }
      return prefix;
    }
  }

  /** Rekeys one private stage created atomically with owner-only permissions. */
  static void rekeyStagedBookCopy(
      Path stagedBookPath,
      SqliteBookPassphrase sourcePassphrase,
      SqliteBookPassphrase replacementPassphrase) {
    try (SqliteBookPassphrase ignoredSource = sourcePassphrase;
        SqliteBookPassphrase ignoredReplacement = replacementPassphrase;
        SqliteBookPassphrase resolvedSourcePassphrase = sourcePassphrase.copy();
        SqliteBookPassphrase resolvedReplacementPassphrase = replacementPassphrase.copy();
        SqliteNativeDatabase stagedDatabase =
            SqliteNativeConnections.open(
                stagedBookPath,
                resolvedSourcePassphrase,
                SqliteNativeOpenMode.READ_WRITE_EXISTING_STAGE)) {
      SqliteNativeKeyConfiguration.rekey(stagedDatabase, resolvedReplacementPassphrase);
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

  static void requireExistingSecureBackupFileParentDirectory(Path artifactPath) {
    try {
      SqliteBookFileSecurity.requireExistingSecureParentDirectory(artifactPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to validate the existing parent directory for "
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

  static void requireExistingSecureBackupKeyFileParentDirectory(Path artifactPath) {
    try {
      SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(artifactPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to validate the existing parent directory for "
              + SqliteMachinePaths.absoluteValue(artifactPath)
              + ".",
          exception);
    }
  }
}
