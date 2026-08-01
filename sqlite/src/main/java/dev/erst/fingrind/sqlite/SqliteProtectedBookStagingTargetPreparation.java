package dev.erst.fingrind.sqlite;

import java.nio.file.Path;

/** Prepares final-target parents and stage recovery for protected-book maintenance staging. */
final class SqliteProtectedBookStagingTargetPreparation {
  private SqliteProtectedBookStagingTargetPreparation() {}

  static void prepareUnreservedBackupTargets(Path backupFilePath, Path backupKeyFilePath) {
    ensureArtifactParents(backupFilePath, backupKeyFilePath);
    SqliteGeneratedSecretTarget.requireAbsent(backupKeyFilePath);
  }

  static void prepareUnreservedRestoreTargets(Path bookFilePath, Path bookKeyFilePath) {
    SqliteBookFileSecurity.requireSupportedSecureFilesystem(bookFilePath);
    SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(bookKeyFilePath);
    ensureArtifactParents(bookFilePath, bookKeyFilePath);
    SqliteGeneratedSecretTarget.requireAbsent(bookKeyFilePath);
  }

  static void ensureArtifactParents(Path bookArtifactPath, Path secretArtifactPath) {
    SqliteProtectedBookStagingFiles.requireExistingSecureBackupFileParentDirectory(
        bookArtifactPath);
    SqliteProtectedBookStagingFiles.requireExistingSecureBackupKeyFileParentDirectory(
        secretArtifactPath);
  }
}
