package dev.erst.fingrind.sqlite;

import java.nio.file.Path;

/** Prepares final-target parents and stage recovery for protected-book maintenance staging. */
final class SqliteProtectedBookStagingTargetPreparation {
  private SqliteProtectedBookStagingTargetPreparation() {}

  static void ensureArtifactParents(Path bookArtifactPath, Path secretArtifactPath) {
    SqliteProtectedBookStagingFiles.requireExistingSecureBackupFileParentDirectory(
        bookArtifactPath);
    SqliteProtectedBookStagingFiles.requireExistingSecureBackupKeyFileParentDirectory(
        secretArtifactPath);
  }
}
